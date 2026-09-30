package com.ferrocompras.ordenescompra.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraRepository;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenEventoProveedorRepository;
import com.ferrocompras.ordenescompra.shared.entity.Producto;
import com.ferrocompras.ordenescompra.shared.entity.Proveedor;
import com.ferrocompras.ordenescompra.shared.entity.Sucursal;
import com.ferrocompras.ordenescompra.shared.enums.FormatoSucursal;
import com.ferrocompras.ordenescompra.shared.repository.ProductoRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProveedorRepository;
import com.ferrocompras.ordenescompra.shared.repository.SucursalRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebhookControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private SucursalRepository sucursalRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private OrdenCompraRepository ordenCompraRepository;
    @Autowired private OrdenEventoProveedorRepository eventoProveedorRepository;

    private String tokenComprador;
    private Proveedor proveedor;
    private Integer ordenId;
    private String numeroOrden;

    @BeforeEach
    void setUp() throws Exception {
        tokenComprador = login("maria.gomez", "Compras2026!");
        proveedor = proveedorRepository.findAll().get(0);
        Sucursal ferreteria = sucursalRepository.findAll().stream()
            .filter(s -> s.getFormato() == FormatoSucursal.FERRETERIA).findFirst().orElseThrow();
        Producto tornillo = productoRepository.findAll().stream()
            .filter(p -> p.getCodigo().equals("TORN-05")).findFirst().orElseThrow();

        String body = """
            {"proveedor_id": %d, "sucursal_destino_id": %d, "fecha_necesaria": "%s",
             "detalle": [{"producto_id": %d, "cantidad": 10}]}
            """.formatted(proveedor.getId(), ferreteria.getId(), LocalDate.now().plusDays(10), tornillo.getId());

        String json = mockMvc.perform(post("/api/ordenes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        ordenId = JsonPath.read(json, "$.id");
        numeroOrden = JsonPath.read(json, "$.numero_orden");

        mockMvc.perform(patch("/api/ordenes/{id}/aprobar", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username": "%s", "password": "%s"}
                    """.formatted(username, password)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.access_token");
    }

    private String eventoBody(String numeroOrden, String tipoEvento) {
        // fecha_evento debe quedar despues de la creacion/aprobacion real de la orden (timestamps
        // de reloj real) para que el orden de la linea de tiempo sea determinista en el test.
        return """
            {"numero_orden": "%s", "tipo_evento": "%s", "observacion": "prueba",
             "fecha_evento": "%s"}
            """.formatted(numeroOrden, tipoEvento, Instant.now().plusSeconds(60));
    }

    @Test
    void unApiKeyInvalidaDevuelve401() throws Exception {
        mockMvc.perform(post("/api/webhooks/eventos")
                .header("X-Api-Key", "clave-que-no-existe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventoBody(numeroOrden, "DESPACHADA")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unaOrdenDeOtroProveedorDevuelve404() throws Exception {
        Proveedor otroProveedor = proveedorRepository.findAll().stream()
            .filter(p -> !p.getId().equals(proveedor.getId())).findFirst().orElseThrow();

        mockMvc.perform(post("/api/webhooks/eventos")
                .header("X-Api-Key", otroProveedor.getWebhookApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventoBody(numeroOrden, "DESPACHADA")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.param").value("numero_orden"));

        assertThat(eventoProveedorRepository.findByOrdenCompraIdOrderByFechaEventoAsc(ordenId)).isEmpty();
    }

    @Test
    void unEventoValidoQuedaRegistradoYNoCambiaElEstadoDeLaOrden() throws Exception {
        mockMvc.perform(post("/api/webhooks/eventos")
                .header("X-Api-Key", proveedor.getWebhookApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventoBody(numeroOrden, "ENTREGADA")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo_evento").value("ENTREGADA"));

        assertThat(eventoProveedorRepository.findByOrdenCompraIdOrderByFechaEventoAsc(ordenId)).hasSize(1);
        assertThat(ordenCompraRepository.findById(ordenId).orElseThrow().getEstado()).isEqualTo(EstadoOrden.APROBADA);
    }

    @Test
    void repetirElMismoIdempotencyKeyEnElWebhookNoDuplicaElEvento() throws Exception {
        String idempotencyKey = "webhook-key-" + UUID.randomUUID();

        mockMvc.perform(post("/api/webhooks/eventos")
                .header("X-Api-Key", proveedor.getWebhookApiKey())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventoBody(numeroOrden, "DESPACHADA")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/webhooks/eventos")
                .header("X-Api-Key", proveedor.getWebhookApiKey())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventoBody(numeroOrden, "DESPACHADA")))
            .andExpect(status().isCreated());

        assertThat(eventoProveedorRepository.findByOrdenCompraIdOrderByFechaEventoAsc(ordenId)).hasSize(1);
    }

    @Test
    void laMismaIdempotencyKeyDeOtroProveedorNoReproduceLaRespuestaAjena() throws Exception {
        Proveedor otroProveedor = proveedorRepository.findAll().stream()
            .filter(p -> !p.getId().equals(proveedor.getId())).findFirst().orElseThrow();
        String idempotencyKey = "webhook-key-" + UUID.randomUUID();

        mockMvc.perform(post("/api/webhooks/eventos")
                .header("X-Api-Key", proveedor.getWebhookApiKey())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventoBody(numeroOrden, "DESPACHADA")))
            .andExpect(status().isCreated());

        // Misma clave, otro proveedor: debe evaluarse por su cuenta (404 por no ser su orden),
        // no recibir el 201 cacheado del primero.
        mockMvc.perform(post("/api/webhooks/eventos")
                .header("X-Api-Key", otroProveedor.getWebhookApiKey())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventoBody(numeroOrden, "DESPACHADA")))
            .andExpect(status().isNotFound());
    }

    @Test
    void lineaTiempoCombinaAuditoriaInternaYEventosDeProveedorOrdenadosPorFecha() throws Exception {
        mockMvc.perform(post("/api/webhooks/eventos")
                .header("X-Api-Key", proveedor.getWebhookApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventoBody(numeroOrden, "ACEPTADA")))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/ordenes/{id}/linea-tiempo", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].origen").value("interno"))
            .andExpect(jsonPath("$.data[0].tipo").value("CREADA"))
            .andExpect(jsonPath("$.data[1].origen").value("interno"))
            .andExpect(jsonPath("$.data[1].tipo").value("APROBADA"))
            .andExpect(jsonPath("$.data[2].origen").value("proveedor"))
            .andExpect(jsonPath("$.data[2].tipo").value("ACEPTADA"));
    }
}
