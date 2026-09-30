package com.ferrocompras.ordenescompra.moduloadministracion.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferrocompras.ordenescompra.shared.entity.Producto;
import com.ferrocompras.ordenescompra.shared.entity.Proveedor;
import com.ferrocompras.ordenescompra.shared.entity.Sucursal;
import com.ferrocompras.ordenescompra.shared.enums.FormatoSucursal;
import com.ferrocompras.ordenescompra.shared.repository.ProductoRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProveedorRepository;
import com.ferrocompras.ordenescompra.shared.repository.SucursalRepository;
import com.jayway.jsonpath.JsonPath;
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
class AdministracionControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SucursalRepository sucursalRepository;
    @Autowired
    private ProveedorRepository proveedorRepository;
    @Autowired
    private ProductoRepository productoRepository;

    private String tokenAdmin;
    private String tokenComprador;

    @BeforeEach
    void login() throws Exception {
        tokenAdmin = login("admin", "Compras2026!");
        tokenComprador = login("maria.gomez", "Compras2026!");
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

    @Test
    void unCompradorNoPuedeCrearProveedores() throws Exception {
        mockMvc.perform(post("/api/proveedores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre": "Proveedor X", "email": "x@example.com"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void listarProductosPorSucursalDevuelveSoloLosDeCategoriasPermitidasParaSuFormato() throws Exception {
        Sucursal construccion = sucursalRepository.findAll().stream()
            .filter(s -> s.getFormato() == FormatoSucursal.FERRETERIA_CONSTRUCCION).findFirst().orElseThrow();

        mockMvc.perform(get("/api/productos")
                .param("sucursal_id", construccion.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[?(@.codigo == 'CEM-42')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.codigo == 'TORN-05')]").isEmpty());
    }

    @Test
    void cualquierUsuarioAutenticadoListaLasCategoriasDeProducto() throws Exception {
        mockMvc.perform(get("/api/categorias")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(7));
    }

    @Test
    void unCompradorListaProveedoresSinVerLaClaveDeWebhook() throws Exception {
        mockMvc.perform(get("/api/proveedores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].nombre").isNotEmpty())
            .andExpect(jsonPath("$.data[0].webhook_api_key").doesNotExist());

        mockMvc.perform(get("/api/proveedores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(jsonPath("$.data[0].webhook_api_key").isNotEmpty());
    }

    @Test
    void unAdminCreaUnProveedorConWebhookApiKeyGeneradaYActivoPorDefecto() throws Exception {
        mockMvc.perform(post("/api/proveedores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre": "Proveedor Nuevo", "email": "nuevo@example.com"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.activo").value(true))
            .andExpect(jsonPath("$.webhook_api_key").isNotEmpty());
    }

    @Test
    void regenerarLaClaveDeWebhookInvalidaLaAnteriorYActivaLaNueva() throws Exception {
        Proveedor proveedor = proveedorRepository.findAll().get(0);
        String claveAnterior = proveedor.getWebhookApiKey();
        String evento = """
            {"numero_orden": "OC-1999-000001", "tipo_evento": "ACEPTADA", "fecha_evento": "2026-09-25T10:00:00Z"}
            """;

        String json = mockMvc.perform(patch("/api/proveedores/{id}/regenerar-clave-webhook", proveedor.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String claveNueva = JsonPath.read(json, "$.webhook_api_key");

        org.assertj.core.api.Assertions.assertThat(claveNueva).startsWith("whsk_").isNotEqualTo(claveAnterior);

        // La clave anterior deja de autenticar (401); la nueva si (404 = autenticado, orden inexistente).
        mockMvc.perform(post("/api/webhooks/eventos").header("X-Api-Key", claveAnterior)
                .contentType(MediaType.APPLICATION_JSON).content(evento))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/webhooks/eventos").header("X-Api-Key", claveNueva)
                .contentType(MediaType.APPLICATION_JSON).content(evento))
            .andExpect(status().isNotFound());
    }

    @Test
    void soloUnAdminPuedeRegenerarLaClaveDeWebhook() throws Exception {
        Integer id = proveedorRepository.findAll().get(0).getId();

        mockMvc.perform(patch("/api/proveedores/{id}/regenerar-clave-webhook", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/proveedores/{id}/regenerar-clave-webhook", 999999)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isNotFound());
    }

    @Test
    void unAdminPuedeDesactivarUnProveedorExistenteConPut() throws Exception {
        String json = mockMvc.perform(post("/api/proveedores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre": "Proveedor Editable", "email": "editable@example.com"}
                    """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(json, "$.id");

        mockMvc.perform(put("/api/proveedores/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre": "Proveedor Editable", "email": "editable@example.com", "activo": false}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void unCompradorPuedeListarProductosPeroNoCrearlos() throws Exception {
        mockMvc.perform(get("/api/productos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/productos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"codigo": "TEST-01", "nombre": "Producto de prueba", "categoria_id": 1,
                     "precio": 10.0, "unidad_venta": "UNIDAD", "unidad_compra": "UNIDAD"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void crearUnProductoSinFactorConversionUsaUnoPorDefecto() throws Exception {
        String codigo = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/productos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"codigo": "%s", "nombre": "Producto de prueba", "categoria_id": 1,
                     "precio": 10.0, "unidad_venta": "UNIDAD", "unidad_compra": "UNIDAD"}
                    """.formatted(codigo)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.factor_conversion").value(1));
    }

    @Test
    void crearUnUsuarioGerenteSucursalSinSucursalIdDevuelve400() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username": "usuario.sin.sucursal", "password": "ClaveSegura1!", "rol": "GERENTE_SUCURSAL"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.param").value("sucursal_id"));
    }

    @Test
    void crearUnUsuarioSinPasswordDevuelve400() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username": "sin.password", "rol": "COMPRADOR"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.param").value("password"));
    }

    @Test
    void editarUnUsuarioSinPasswordConservaLaContrasenaActual() throws Exception {
        String json = mockMvc.perform(post("/api/usuarios")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username": "editable.usuario", "password": "ClaveSegura1!", "rol": "COMPRADOR"}
                    """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.id");

        mockMvc.perform(put("/api/usuarios/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username": "editable.usuario", "rol": "ADMIN"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rol").value("ADMIN"));

        login("editable.usuario", "ClaveSegura1!");
    }

    @Test
    void cualquierRolAutenticadoPuedeListarSucursales() throws Exception {
        mockMvc.perform(get("/api/sucursales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk());
    }

    private String jsonSucursal(String nombre, String formato, Boolean activo) {
        return """
            {"nombre": "%s", "formato": "%s"%s}
            """.formatted(nombre, formato, activo == null ? "" : ", \"activo\": " + activo);
    }

    private Integer crearOrdenEn(Sucursal sucursal) throws Exception {
        Proveedor proveedor = proveedorRepository.findAll().get(0);
        Producto tornillo = productoRepository.findAll().stream()
            .filter(p -> p.getCodigo().equals("TORN-05")).findFirst().orElseThrow();
        String json = mockMvc.perform(post("/api/ordenes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"proveedor_id": %d, "sucursal_destino_id": %d, "fecha_necesaria": "%s",
                     "detalle": [{"producto_id": %d, "cantidad": 5}]}
                    """.formatted(proveedor.getId(), sucursal.getId(), LocalDate.now().plusDays(5), tornillo.getId())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void unAdminCreaUnaSucursalActivaPorDefectoYUnCompradorNoPuede() throws Exception {
        mockMvc.perform(post("/api/sucursales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("Sucursal Norte", "FERRETERIA", null)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/sucursales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("Sucursal Norte", "FERRETERIA", null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("Sucursal Norte"))
            .andExpect(jsonPath("$.formato").value("FERRETERIA"))
            .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void crearUnaSucursalSinNombreOConFormatoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/sucursales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("  ", "FERRETERIA", null)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sucursales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("Sucursal Sur", "MAYORISTA", null)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void editarUnaSucursalPermiteRenombrarYDesactivarYSinActivoConservaElValor() throws Exception {
        Integer id = JsonPath.read(mockMvc.perform(post("/api/sucursales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("Sucursal Este", "FERRETERIA", null)))
            .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/sucursales/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("Sucursal Este II", "FERRETERIA", false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("Sucursal Este II"))
            .andExpect(jsonPath("$.activo").value(false));

        // Sin "activo" en el cuerpo: no reactiva ni desactiva nada.
        mockMvc.perform(put("/api/sucursales/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("Sucursal Este III", "FERRETERIA", null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(put("/api/sucursales/{id}", 999999)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("Fantasma", "FERRETERIA", null)))
            .andExpect(status().isNotFound());
    }

    @Test
    void elFormatoDeUnaSucursalSinOrdenesSePuedeCambiar() throws Exception {
        Integer id = JsonPath.read(mockMvc.perform(post("/api/sucursales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("Sucursal Nueva", "FERRETERIA", null)))
            .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/sucursales/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("Sucursal Nueva", "VENTA_DIRECTA", null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.formato").value("VENTA_DIRECTA"));
    }

    @Test
    void elFormatoDeUnaSucursalConOrdenesNoSePuedeCambiarPeroSuNombreSi() throws Exception {
        Sucursal ferreteria = sucursalRepository.findAll().stream()
            .filter(s -> s.getFormato() == FormatoSucursal.FERRETERIA).findFirst().orElseThrow();
        crearOrdenEn(ferreteria);

        mockMvc.perform(put("/api/sucursales/{id}", ferreteria.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal(ferreteria.getNombre(), "VENTA_DIRECTA", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.type").value("state_conflict_error"))
            .andExpect(jsonPath("$.error.code").value("formato_bloqueado"))
            .andExpect(jsonPath("$.error.param").value("formato"));

        mockMvc.perform(put("/api/sucursales/{id}", ferreteria.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal("Nombre corregido", "FERRETERIA", null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("Nombre corregido"));
    }

    @Test
    void unaSucursalInactivaNoRecibeOrdenesNuevasPeroUnaOrdenExistenteSiSePuedeSeguirEditando() throws Exception {
        Sucursal ferreteria = sucursalRepository.findAll().stream()
            .filter(s -> s.getFormato() == FormatoSucursal.FERRETERIA).findFirst().orElseThrow();
        Integer ordenId = crearOrdenEn(ferreteria);

        mockMvc.perform(put("/api/sucursales/{id}", ferreteria.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonSucursal(ferreteria.getNombre(), "FERRETERIA", false)))
            .andExpect(status().isOk());

        Proveedor proveedor = proveedorRepository.findAll().get(0);
        Producto tornillo = productoRepository.findAll().stream()
            .filter(p -> p.getCodigo().equals("TORN-05")).findFirst().orElseThrow();
        String cuerpo = """
            {"proveedor_id": %d, "sucursal_destino_id": %d, "fecha_necesaria": "%s",
             "detalle": [{"producto_id": %d, "cantidad": 7}]}
            """.formatted(proveedor.getId(), ferreteria.getId(), LocalDate.now().plusDays(6), tornillo.getId());

        mockMvc.perform(post("/api/ordenes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("sucursal_inactiva"))
            .andExpect(jsonPath("$.error.param").value("sucursal_destino_id"));

        // La orden que ya existia para esa sucursal sigue siendo editable (mismo destino).
        mockMvc.perform(put("/api/ordenes/{id}", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isOk());
    }

    @Test
    void laBitacoraDeAuditoriaMuestraLasTransicionesDeUnaOrden() throws Exception {
        Sucursal ferreteria = sucursalRepository.findAll().stream()
            .filter(s -> s.getFormato() == FormatoSucursal.FERRETERIA).findFirst().orElseThrow();
        Proveedor proveedor = proveedorRepository.findAll().get(0);
        Producto tornillo = productoRepository.findAll().stream()
            .filter(p -> p.getCodigo().equals("TORN-05")).findFirst().orElseThrow();

        String ordenJson = mockMvc.perform(post("/api/ordenes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"proveedor_id": %d, "sucursal_destino_id": %d, "fecha_necesaria": "%s",
                     "detalle": [{"producto_id": %d, "cantidad": 5}]}
                    """.formatted(proveedor.getId(), ferreteria.getId(), LocalDate.now().plusDays(5), tornillo.getId())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String numeroOrden = JsonPath.read(ordenJson, "$.numero_orden");

        mockMvc.perform(get("/api/auditoria/ordenes/{numeroOrden}", numeroOrden)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].estado_nuevo").value("CREADA"));
    }

    @Test
    void laBitacoraDeAuditoriaConUnNumeroDeOrdenInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/auditoria/ordenes/{numeroOrden}", "OC-2026-999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("orden_no_encontrada"))
            .andExpect(jsonPath("$.error.param").value("numero_orden"));
    }
}
