package com.ferrocompras.ordenescompra.modulocompras.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraAuditoriaRepository;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraRepository;
import com.ferrocompras.ordenescompra.shared.entity.Cliente;
import com.ferrocompras.ordenescompra.shared.entity.Producto;
import com.ferrocompras.ordenescompra.shared.entity.Proveedor;
import com.ferrocompras.ordenescompra.shared.entity.Sucursal;
import com.ferrocompras.ordenescompra.shared.enums.FormatoSucursal;
import com.ferrocompras.ordenescompra.shared.repository.ClienteRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProductoRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProveedorRepository;
import com.ferrocompras.ordenescompra.shared.repository.SucursalRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrdenCompraControllerIntegrationTest {

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
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private OrdenCompraAuditoriaRepository auditoriaRepository;
    @Autowired
    private OrdenCompraRepository ordenCompraRepository;

    private String tokenComprador;
    private String tokenGerenteSanSalvador;
    private String tokenGerenteSantaAna;

    @BeforeEach
    void login() throws Exception {
        tokenComprador = login("maria.gomez", "Compras2026!");
        tokenGerenteSanSalvador = login("gerente.sansalvador", "Compras2026!");
        tokenGerenteSantaAna = login("gerente.santaana", "Compras2026!");
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

    private Sucursal sucursalDeFormato(FormatoSucursal formato) {
        return sucursalRepository.findAll().stream()
            .filter(s -> s.getFormato() == formato)
            .findFirst().orElseThrow();
    }

    private Producto productoPorCodigo(String codigo) {
        return productoRepository.findAll().stream()
            .filter(p -> p.getCodigo().equals(codigo))
            .findFirst().orElseThrow();
    }

    private Integer crearOrdenFerreteria(String token) throws Exception {
        return crearOrdenFerreteria(token, LocalDate.now().plusDays(10));
    }

    private Integer crearOrdenFerreteria(String token, LocalDate fechaNecesaria) throws Exception {
        Proveedor proveedor = proveedorRepository.findAll().get(0);
        Sucursal ferreteria = sucursalDeFormato(FormatoSucursal.FERRETERIA);
        Producto tornillo = productoPorCodigo("TORN-05"); // HERRAMIENTAS, permitido en Ferreteria

        String body = """
            {"proveedor_id": %d, "sucursal_destino_id": %d, "fecha_necesaria": "%s",
             "detalle": [{"producto_id": %d, "cantidad": 10}]}
            """.formatted(proveedor.getId(), ferreteria.getId(), fechaNecesaria, tornillo.getId());

        String json = mockMvc.perform(post("/api/ordenes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        return JsonPath.read(json, "$.id");
    }

    private String crearOrdenVentaDirecta(String token, Integer clienteId) throws Exception {
        Proveedor proveedor = proveedorRepository.findAll().get(0);
        Sucursal ventaDirecta = sucursalDeFormato(FormatoSucursal.VENTA_DIRECTA);
        Producto tornillo = productoPorCodigo("TORN-05"); // HERRAMIENTAS, tambien permitido en Venta Directa

        String body = """
            {"proveedor_id": %d, "sucursal_destino_id": %d, "cliente_id": %d, "fecha_necesaria": "%s",
             "detalle": [{"producto_id": %d, "cantidad": 5}]}
            """.formatted(proveedor.getId(), ventaDirecta.getId(), clienteId, LocalDate.now().plusDays(10), tornillo.getId());

        String json = mockMvc.perform(post("/api/ordenes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        return JsonPath.read(json, "$.numero_orden");
    }

    private void aprobar(Integer ordenId, String token) throws Exception {
        mockMvc.perform(patch("/api/ordenes/{id}/aprobar", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }

    private void cerrar(Integer ordenId, String token) throws Exception {
        mockMvc.perform(patch("/api/ordenes/{id}/cerrar", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conforme\": true}"))
            .andExpect(status().isOk());
    }

    @Test
    void compradorCreaUnaOrdenCompletaYElTotalQuedaCalculado() throws Exception {
        Producto tornillo = productoPorCodigo("TORN-05");
        Integer ordenId = crearOrdenFerreteria(tokenComprador);

        mockMvc.perform(get("/api/ordenes/{id}", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CREADA"))
            .andExpect(jsonPath("$.total").value(tornillo.getPrecio().doubleValue() * 10));
    }

    @Test
    void aprobarUnaOrdenRegistraLaTransicionEnAuditoria() throws Exception {
        Integer ordenId = crearOrdenFerreteria(tokenComprador);

        aprobar(ordenId, tokenComprador);

        boolean auditoriaRegistrada = auditoriaRepository.findAll().stream()
            .anyMatch(a -> a.getOrdenCompra().getId().equals(ordenId) && a.getEstadoNuevo() == EstadoOrden.APROBADA);
        assertThat(auditoriaRegistrada).isTrue();
    }

    @Test
    void anularUnaOrdenCerradaResponde409() throws Exception {
        Integer ordenId = crearOrdenFerreteria(tokenComprador);
        aprobar(ordenId, tokenComprador);
        cerrar(ordenId, tokenGerenteSanSalvador);

        mockMvc.perform(patch("/api/ordenes/{id}/anular", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"intento invalido\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.type").value("state_conflict_error"));
    }

    @Test
    void unGerenteDeOtraSucursalNoPuedeVerNiCerrarLaOrden() throws Exception {
        Integer ordenId = crearOrdenFerreteria(tokenComprador);
        aprobar(ordenId, tokenComprador);

        mockMvc.perform(get("/api/ordenes/{id}", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteSantaAna))
            .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/ordenes/{id}/cerrar", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteSantaAna)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conforme\": true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unCompradorNoPuedeEjecutarCerrar() throws Exception {
        Integer ordenId = crearOrdenFerreteria(tokenComprador);
        aprobar(ordenId, tokenComprador);

        mockMvc.perform(patch("/api/ordenes/{id}/cerrar", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conforme\": true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void repetirElMismoIdempotencyKeyDevuelveLaMismaRespuestaSinDuplicarLaOrden() throws Exception {
        Proveedor proveedor = proveedorRepository.findAll().get(0);
        Sucursal ferreteria = sucursalDeFormato(FormatoSucursal.FERRETERIA);
        Producto tornillo = productoPorCodigo("TORN-05");
        String body = """
            {"proveedor_id": %d, "sucursal_destino_id": %d, "fecha_necesaria": "%s",
             "detalle": [{"producto_id": %d, "cantidad": 5}]}
            """.formatted(proveedor.getId(), ferreteria.getId(), LocalDate.now().plusDays(10), tornillo.getId());
        String idempotencyKey = "test-key-" + UUID.randomUUID();

        String primeraRespuesta = mockMvc.perform(post("/api/ordenes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        long ordenesLuegoDeLaPrimera = ordenCompraRepository.count();

        String segundaRespuesta = mockMvc.perform(post("/api/ordenes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        assertThat(segundaRespuesta).isEqualTo(primeraRespuesta);
        assertThat(ordenCompraRepository.count()).isEqualTo(ordenesLuegoDeLaPrimera);
    }

    @Test
    void expandProveedorEmbebeElProveedorCompletoSoloCuandoSePide() throws Exception {
        Integer ordenId = crearOrdenFerreteria(tokenComprador);
        Proveedor proveedor = proveedorRepository.findAll().get(0);

        mockMvc.perform(get("/api/ordenes/{id}", ordenId)
                .param("expand", "proveedor")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proveedor.id").value(proveedor.getId()))
            .andExpect(jsonPath("$.proveedor.nombre").value(proveedor.getNombre()));

        mockMvc.perform(get("/api/ordenes/{id}", ordenId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proveedor").doesNotExist());
    }

    @Test
    void listarSoportaPaginacionPorCursor() throws Exception {
        crearOrdenFerreteria(tokenComprador);
        crearOrdenFerreteria(tokenComprador);
        crearOrdenFerreteria(tokenComprador);
        Integer sucursalId = sucursalDeFormato(FormatoSucursal.FERRETERIA).getId();

        String primeraPaginaJson = mockMvc.perform(get("/api/ordenes")
                .param("limit", "2")
                .param("sucursal_id", String.valueOf(sucursalId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.has_more").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andReturn().getResponse().getContentAsString();

        String ultimoNumeroOrden = JsonPath.read(primeraPaginaJson, "$.data[1].numero_orden");

        mockMvc.perform(get("/api/ordenes")
                .param("limit", "2")
                .param("starting_after", ultimoNumeroOrden)
                .param("sucursal_id", String.valueOf(sucursalId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.has_more").value(false))
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void listarFiltraPorClienteYPorNumeroOrdenParcial() throws Exception {
        Integer clienteId = clienteRepository.findAll().get(0).getId();
        String numeroOrdenVentaDirecta = crearOrdenVentaDirecta(tokenComprador, clienteId);
        crearOrdenFerreteria(tokenComprador); // sin cliente: no debe aparecer al filtrar por cliente_id

        mockMvc.perform(get("/api/ordenes")
                .param("cliente_id", String.valueOf(clienteId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].numero_orden").value(numeroOrdenVentaDirecta));

        String fragmento = numeroOrdenVentaDirecta.substring(numeroOrdenVentaDirecta.length() - 4).toLowerCase();
        mockMvc.perform(get("/api/ordenes")
                .param("numero_orden", fragmento)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].numero_orden").value(numeroOrdenVentaDirecta));
    }

    @Test
    void listarFiltraPorRangoDeFechaNecesaria() throws Exception {
        crearOrdenFerreteria(tokenComprador, LocalDate.now().plusDays(5));
        crearOrdenFerreteria(tokenComprador, LocalDate.now().plusDays(15));
        crearOrdenFerreteria(tokenComprador, LocalDate.now().plusDays(25));

        // Solo desde: incluye la del dia 15 y la del dia 25.
        mockMvc.perform(get("/api/ordenes")
                .param("fecha_desde", LocalDate.now().plusDays(10).toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));

        // Solo hasta: incluye la del dia 5 y la del dia 15.
        mockMvc.perform(get("/api/ordenes")
                .param("fecha_hasta", LocalDate.now().plusDays(20).toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));

        // Ambas: solo la del dia 15 queda dentro del rango.
        mockMvc.perform(get("/api/ordenes")
                .param("fecha_desde", LocalDate.now().plusDays(10).toString())
                .param("fecha_hasta", LocalDate.now().plusDays(20).toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));

        // Rango invertido: se rechaza con 400, no se interpreta silenciosamente al reves.
        mockMvc.perform(get("/api/ordenes")
                .param("fecha_desde", LocalDate.now().plusDays(20).toString())
                .param("fecha_hasta", LocalDate.now().plusDays(10).toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("rango_fecha_invalido"));
    }

    private static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] COLUMNAS_ORDENES = {
        "referencia_lote", "proveedor", "sucursal_destino", "cliente", "fecha_necesaria", "producto_codigo", "cantidad"
    };

    // Construye un .xlsx minimo con una hoja "Ordenes": cada elemento de `filas` es una fila en el
    // mismo orden que COLUMNAS_ORDENES (null para una celda vacia, p. ej. cliente).
    private MockMultipartFile archivoOrdenesXlsx(String nombreArchivo, Object[]... filas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("Ordenes");
            Row encabezado = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS_ORDENES.length; c++) {
                encabezado.createCell(c).setCellValue(COLUMNAS_ORDENES[c]);
            }
            for (int f = 0; f < filas.length; f++) {
                Row fila = hoja.createRow(f + 1);
                Object[] valores = filas[f];
                for (int c = 0; c < valores.length; c++) {
                    if (valores[c] == null) {
                        continue;
                    }
                    if (valores[c] instanceof Number numero) {
                        fila.createCell(c).setCellValue(numero.doubleValue());
                    } else {
                        fila.createCell(c).setCellValue(valores[c].toString());
                    }
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return new MockMultipartFile("archivo", nombreArchivo, MIME_XLSX, salida.toByteArray());
        }
    }

    @Test
    void descargarPlantillaDevuelveUnExcelDeEjemplo() throws Exception {
        byte[] contenido = mockMvc.perform(get("/api/ordenes/importaciones/plantilla")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MIME_XLSX)))
            .andReturn().getResponse().getContentAsByteArray();

        try (var libro = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(contenido))) {
            assertThat(libro.getSheet("Ordenes")).isNotNull();
            assertThat(libro.getSheet("Proveedores")).isNotNull();
            assertThat(libro.getSheet("Sucursales")).isNotNull();
            assertThat(libro.getSheet("Clientes")).isNotNull();
            assertThat(libro.getSheet("Productos")).isNotNull();
            Row encabezado = libro.getSheet("Ordenes").getRow(0);
            assertThat(encabezado.getCell(0).getStringCellValue()).isEqualTo("referencia_lote");
            assertThat(encabezado.getCell(1).getStringCellValue()).isEqualTo("proveedor");
        }
    }

    @Test
    void unGerenteDeSucursalNoPuedeImportarOrdenes() throws Exception {
        MockMultipartFile archivo = archivoOrdenesXlsx("ordenes.xlsx");

        mockMvc.perform(multipart("/api/ordenes/importaciones")
                .file(archivo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteSanSalvador))
            .andExpect(status().isForbidden());
    }

    @Test
    void importarUnExcelConLotesValidosEInvalidosCreaSoloLosValidosYReportaElResto() throws Exception {
        Proveedor proveedor = proveedorRepository.findAll().get(0);
        Sucursal ferreteria = sucursalDeFormato(FormatoSucursal.FERRETERIA);
        String fecha = LocalDate.now().plusDays(10).toString();
        long ordenesAntes = ordenCompraRepository.count();

        MockMultipartFile archivo = archivoOrdenesXlsx("ordenes.xlsx",
            new Object[] {"LOTE-VALIDO", proveedor.getNombre(), ferreteria.getNombre(), null, fecha, "TORN-05", 10},
            new Object[] {"LOTE-CATEGORIA", proveedor.getNombre(), ferreteria.getNombre(), null, fecha, "CEM-42", 5},
            new Object[] {"LOTE-PRODUCTO", proveedor.getNombre(), ferreteria.getNombre(), null, fecha, "NOPE-99", 1});

        String json = mockMvc.perform(multipart("/api/ordenes/importaciones")
                .file(archivo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.object").value("importacion_lote"))
            .andExpect(jsonPath("$.ordenes_creadas.length()").value(1))
            .andExpect(jsonPath("$.ordenes_creadas[0].referencia_lote").value("LOTE-VALIDO"))
            .andExpect(jsonPath("$.ordenes_rechazadas.length()").value(2))
            .andReturn().getResponse().getContentAsString();

        List<String> codigosCategoria = JsonPath.read(json,
            "$.ordenes_rechazadas[?(@.referencia_lote=='LOTE-CATEGORIA')].error.code");
        assertThat(codigosCategoria).containsExactly("categoria_no_permitida");
        List<String> paramsCategoria = JsonPath.read(json,
            "$.ordenes_rechazadas[?(@.referencia_lote=='LOTE-CATEGORIA')].error.param");
        assertThat(paramsCategoria).containsExactly("producto_codigo");

        List<String> codigosProducto = JsonPath.read(json,
            "$.ordenes_rechazadas[?(@.referencia_lote=='LOTE-PRODUCTO')].error.code");
        assertThat(codigosProducto).containsExactly("producto_no_encontrado");

        assertThat(ordenCompraRepository.count()).isEqualTo(ordenesAntes + 1);
    }

    @Test
    void importarUnLoteConElMismoProductoEnVariasFilasAgrupaLasCantidades() throws Exception {
        Proveedor proveedor = proveedorRepository.findAll().get(0);
        Sucursal ferreteria = sucursalDeFormato(FormatoSucursal.FERRETERIA);
        String fecha = LocalDate.now().plusDays(10).toString();

        MockMultipartFile archivo = archivoOrdenesXlsx("ordenes.xlsx",
            new Object[] {"LOTE-DUP", proveedor.getNombre(), ferreteria.getNombre(), null, fecha, "TORN-05", 10},
            new Object[] {"LOTE-DUP", proveedor.getNombre(), ferreteria.getNombre(), null, fecha, "TORN-05", 15});

        String json = mockMvc.perform(multipart("/api/ordenes/importaciones")
                .file(archivo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ordenes_creadas.length()").value(1))
            .andReturn().getResponse().getContentAsString();

        Integer ordenId = JsonPath.read(json, "$.ordenes_creadas[0].orden_id");
        OrdenCompra creada = ordenCompraRepository.findById(ordenId).orElseThrow();
        assertThat(creada.getDetalle()).hasSize(1);
        assertThat(creada.getDetalle().get(0).getCantidad()).isEqualByComparingTo("25");
    }

    @Test
    void importarUnArchivoQueNoEsExcelValidoDevuelveErrorDeArchivoYNoUn401() throws Exception {
        // Regresion: cualquier problema al leer el archivo (formato incorrecto, encabezado
        // incompleto, etc.) debe reportarse como un 400 de negocio, nunca dejar que una excepcion
        // sin capturar se propague hasta el filtro de seguridad y se confunda con un 401 de
        // autenticacion invalida - un mensaje enganoso sin relacion con el token del usuario.
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "ordenes.xlsx", MIME_XLSX, "esto no es un archivo excel".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/ordenes/importaciones")
                .file(archivo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("archivo_invalido"));
    }

    @Test
    void importarUnExcelSinUnaColumnaObligatoriaEsRechazadoConMensajeClaro() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("Ordenes");
            Row encabezado = hoja.createRow(0);
            // Falta "sucursal_destino" a proposito.
            String[] columnas = {"referencia_lote", "proveedor", "cliente", "fecha_necesaria", "producto_codigo", "cantidad"};
            for (int c = 0; c < columnas.length; c++) {
                encabezado.createCell(c).setCellValue(columnas[c]);
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            MockMultipartFile archivo = new MockMultipartFile("archivo", "ordenes.xlsx", MIME_XLSX, salida.toByteArray());

            mockMvc.perform(multipart("/api/ordenes/importaciones")
                    .file(archivo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("archivo_invalido"))
                .andExpect(jsonPath("$.error.message").value(containsString("sucursal_destino")));
        }
    }

    @Test
    void exportarUnaOrdenComoPdfDevuelveUnDocumentoValido() throws Exception {
        Integer ordenId = crearOrdenFerreteria(tokenComprador);

        byte[] pdf = mockMvc.perform(get("/api/ordenes/{id}/exportar", ordenId)
                .param("formato", "pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("application/pdf")))
            .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void unGerenteDeOtraSucursalNoPuedeExportarLaOrden() throws Exception {
        Integer ordenId = crearOrdenFerreteria(tokenComprador);

        mockMvc.perform(get("/api/ordenes/{id}/exportar", ordenId)
                .param("formato", "pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerenteSantaAna))
            .andExpect(status().isForbidden());
    }

    @Test
    void unFormatoNoSoportadoEnExportarOrdenDevuelve400() throws Exception {
        Integer ordenId = crearOrdenFerreteria(tokenComprador);

        mockMvc.perform(get("/api/ordenes/{id}/exportar", ordenId)
                .param("formato", "xlsx")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("formato_no_soportado"));
    }

    @Test
    void exportarElListadoComoExcelIncluyeUnaFilaPorLineaDeProducto() throws Exception {
        Proveedor proveedor = proveedorRepository.findAll().get(0);
        Sucursal ferreteria = sucursalDeFormato(FormatoSucursal.FERRETERIA);
        Producto tornillo = productoPorCodigo("TORN-05");
        Producto brocas = productoPorCodigo("BROC-12");

        String body = """
            {"proveedor_id": %d, "sucursal_destino_id": %d, "fecha_necesaria": "%s",
             "detalle": [{"producto_id": %d, "cantidad": 3}, {"producto_id": %d, "cantidad": 2}]}
            """.formatted(proveedor.getId(), ferreteria.getId(), LocalDate.now().plusDays(10),
                tornillo.getId(), brocas.getId());

        mockMvc.perform(post("/api/ordenes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        byte[] excel = mockMvc.perform(get("/api/ordenes/exportar")
                .param("formato", "xlsx")
                .param("sucursal_id", String.valueOf(ferreteria.getId()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MIME_XLSX)))
            .andReturn().getResponse().getContentAsByteArray();

        try (var libro = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(excel))) {
            Sheet hoja = libro.getSheet("Ordenes");
            Row encabezado = hoja.getRow(0);
            assertThat(encabezado.getCell(0).getStringCellValue()).isEqualTo("numero_orden");
            assertThat(encabezado.getCell(3).getStringCellValue()).isEqualTo("cliente");
            assertThat(encabezado.getCell(6).getStringCellValue()).isEqualTo("producto_codigo");

            assertThat(hoja.getLastRowNum()).isEqualTo(2); // encabezado + 2 filas de detalle
            // Sucursal de formato FERRETERIA: la orden no tiene cliente, la celda queda vacia.
            assertThat(hoja.getRow(1).getCell(3)).isNull();
            assertThat(hoja.getRow(1).getCell(6).getStringCellValue()).isEqualTo("TORN-05");
            assertThat(hoja.getRow(2).getCell(6).getStringCellValue()).isEqualTo("BROC-12");
        }
    }
}
