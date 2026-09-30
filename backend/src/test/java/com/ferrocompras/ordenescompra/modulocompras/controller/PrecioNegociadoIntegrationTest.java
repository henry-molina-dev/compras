package com.ferrocompras.ordenescompra.modulocompras.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Precio negociado: cada proveedor define cuanto se puede negociar sobre el precio de catalogo de
// sus productos. Datos semilla (V3): Ferretera del Norte 10% descuento / 5% aumento, Aceros y
// Materiales 5% / 0%, Suministros Industriales 0% / 0% (no negocia). El producto TORN-05 se fija en
// 100.00 para que los limites sean cifras redondas.
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrecioNegociadoIntegrationTest {

    private static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] COLUMNAS = {
        "referencia_lote", "proveedor", "sucursal_destino", "cliente", "fecha_necesaria", "producto_codigo", "cantidad",
        "precio_unitario"};

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProveedorRepository proveedorRepository;
    @Autowired
    private SucursalRepository sucursalRepository;
    @Autowired
    private ProductoRepository productoRepository;

    private String tokenComprador;
    private String tokenAdmin;
    private Proveedor norte;
    private Proveedor aceros;
    private Proveedor suministros;
    private Sucursal ferreteria;
    private Producto tornillo;

    @BeforeEach
    void preparar() throws Exception {
        tokenComprador = login("maria.gomez");
        tokenAdmin = login("admin");
        norte = proveedor("Ferretera del Norte S.A.");
        aceros = proveedor("Aceros y Materiales S.A.");
        suministros = proveedor("Suministros Industriales S.A.");
        ferreteria = sucursalRepository.findAll().stream()
            .filter(s -> s.getFormato() == FormatoSucursal.FERRETERIA).findFirst().orElseThrow();
        tornillo = productoRepository.findByCodigo("TORN-05").orElseThrow();
        fijarPrecioDeCatalogo(tornillo, "100.00");
    }

    private String login(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username": "%s", "password": "Compras2026!"}
                    """.formatted(username)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.access_token");
    }

    private Proveedor proveedor(String nombre) {
        return proveedorRepository.findAll().stream().filter(p -> p.getNombre().equals(nombre)).findFirst().orElseThrow();
    }

    private void fijarPrecioDeCatalogo(Producto producto, String precio) {
        producto.setPrecio(new BigDecimal(precio));
        productoRepository.saveAndFlush(producto);
    }

    private String linea(Producto producto, int cantidad, String precio) {
        return "{\"producto_id\": %d, \"cantidad\": %d%s}".formatted(producto.getId(), cantidad,
            precio == null ? "" : ", \"precio_unitario\": " + precio);
    }

    private String cuerpoOrden(Proveedor proveedor, String... lineas) {
        return """
            {"proveedor_id": %d, "sucursal_destino_id": %d, "fecha_necesaria": "%s", "detalle": [%s]}
            """.formatted(proveedor.getId(), ferreteria.getId(), LocalDate.now().plusDays(10), String.join(",", lineas));
    }

    private ResultActions crear(Proveedor proveedor, String... lineas) throws Exception {
        return mockMvc.perform(post("/api/ordenes")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoOrden(proveedor, lineas)));
    }

    private ResultActions editar(Integer id, Proveedor proveedor, String... lineas) throws Exception {
        return mockMvc.perform(put("/api/ordenes/{id}", id)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoOrden(proveedor, lineas)));
    }

    private Integer crearOrdenNegociada(Proveedor proveedor, String precio) throws Exception {
        String json = crear(proveedor, linea(tornillo, 10, precio)).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    // --- Creacion: limites del proveedor ---

    @Test
    void unaLineaPuedeNegociarseDentroDeLosLimitesYGuardaElPrecioDeCatalogo() throws Exception {
        crear(norte, linea(tornillo, 10, "92.50"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.detalle[0].precio_unitario").value(92.5))
            .andExpect(jsonPath("$.detalle[0].precio_catalogo").value(100.0))
            .andExpect(jsonPath("$.detalle[0].subtotal").value(925.0))
            .andExpect(jsonPath("$.total").value(925.0));
    }

    @Test
    void sinPrecioLaLineaUsaElDeCatalogo() throws Exception {
        crear(norte, linea(tornillo, 10, null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.detalle[0].precio_unitario").value(100.0))
            .andExpect(jsonPath("$.detalle[0].precio_catalogo").value(100.0))
            .andExpect(jsonPath("$.total").value(1000.0));
    }

    @Test
    void losLimitesSonInclusivos() throws Exception {
        crear(norte, linea(tornillo, 1, "90.00")).andExpect(status().isCreated());
        crear(norte, linea(tornillo, 1, "105.00")).andExpect(status().isCreated());
    }

    @Test
    void unPrecioFueraDeLosLimitesSeRechazaConElRangoPermitido() throws Exception {
        crear(norte, linea(tornillo, 1, "89.99"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("precio_fuera_de_rango"))
            .andExpect(jsonPath("$.error.param").value("detalle[0].precio_unitario"))
            .andExpect(jsonPath("$.error.message").value(containsString("entre 90.00 y 105.00")));

        crear(norte, linea(tornillo, 1, "105.01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("precio_fuera_de_rango"));
    }

    // Regresion del error tipico que los limites evitan: un cero de mas al teclear el precio.
    @Test
    void unErrorDeDigitacionComoUnCeroDeMasNoPasa() throws Exception {
        crear(norte, linea(tornillo, 1, "1000.00")).andExpect(status().isBadRequest());
    }

    @Test
    void unProveedorSinMargenDeNegociacionSoloAceptaElPrecioDeCatalogo() throws Exception {
        crear(suministros, linea(tornillo, 1, "99.00"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("precio_fuera_de_rango"))
            .andExpect(jsonPath("$.error.message").value(containsString("no admite negociar")));

        crear(suministros, linea(tornillo, 1, "100.00")).andExpect(status().isCreated());
    }

    @Test
    void losLimitesSonIndependientesPorProveedorYPorSentido() throws Exception {
        // Aceros y Materiales: hasta 5% de descuento y ningun aumento.
        crear(aceros, linea(tornillo, 1, "95.00")).andExpect(status().isCreated());
        crear(aceros, linea(tornillo, 1, "100.01")).andExpect(status().isBadRequest());
        crear(aceros, linea(tornillo, 1, "94.99")).andExpect(status().isBadRequest());
    }

    @Test
    void elPrecioDebeSerPositivoYTenerComoMaximoDosDecimales() throws Exception {
        crear(norte, linea(tornillo, 1, "0")).andExpect(status().isBadRequest());
        crear(norte, linea(tornillo, 1, "95.555")).andExpect(status().isBadRequest());
    }

    // --- Edicion: la referencia es el precio de catalogo guardado en la linea ---

    @Test
    void alEditarUnaLineaSinNegociarConservaSuPrecioYNoTomaElDelCatalogoActual() throws Exception {
        Integer id = crearOrdenNegociada(norte, "92.50");
        fijarPrecioDeCatalogo(tornillo, "120.00"); // el catalogo se movio despues de crear la orden

        editar(id, norte, linea(tornillo, 10, null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.detalle[0].precio_unitario").value(92.5))
            .andExpect(jsonPath("$.detalle[0].precio_catalogo").value(100.0))
            .andExpect(jsonPath("$.total").value(925.0));
    }

    @Test
    void alEditarUnaLineaSinNegociarQueEstabaAlPrecioDeCatalogoConservaEsePrecio() throws Exception {
        Integer id = crearOrdenNegociada(norte, null);
        fijarPrecioDeCatalogo(tornillo, "120.00");

        // Este es el defecto corregido: antes la edicion re-tomaba el precio del catalogo actual (120).
        editar(id, norte, linea(tornillo, 10, null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.detalle[0].precio_unitario").value(100.0))
            .andExpect(jsonPath("$.total").value(1000.0));
    }

    @Test
    void alEditarSeValidaContraElPrecioDeCatalogoGuardadoNoContraElActual() throws Exception {
        Integer id = crearOrdenNegociada(norte, "92.50");
        fijarPrecioDeCatalogo(tornillo, "120.00");

        // Contra el catalogo guardado (100) el rango es 90-105; contra el actual (120) seria 108-126.
        editar(id, norte, linea(tornillo, 10, "91.00")).andExpect(status().isOk());
        editar(id, norte, linea(tornillo, 10, "80.00"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("precio_fuera_de_rango"));
    }

    @Test
    void unProductoNuevoAlEditarUsaElPrecioDeCatalogoActual() throws Exception {
        Integer id = crearOrdenNegociada(norte, null);
        String productos = mockMvc.perform(get("/api/productos")
                .param("sucursal_id", String.valueOf(ferreteria.getId()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> otros = JsonPath.read(productos, "$.data[?(@.codigo != 'TORN-05' && @.activo == true)]");
        Map<String, Object> otro = otros.get(0);
        Integer otroId = (Integer) otro.get("id");
        Double precioOtro = ((Number) otro.get("precio")).doubleValue();

        String json = mockMvc.perform(put("/api/ordenes/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoOrden(norte, linea(tornillo, 10, null), "{\"producto_id\": %d, \"cantidad\": 2}".formatted(otroId))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<Number> catalogo = JsonPath.read(json, "$.detalle[?(@.producto_id == " + otroId + ")].precio_catalogo");
        List<Number> unitario = JsonPath.read(json, "$.detalle[?(@.producto_id == " + otroId + ")].precio_unitario");
        assertThat(catalogo.get(0).doubleValue()).isEqualTo(precioOtro);
        assertThat(unitario.get(0).doubleValue()).isEqualTo(precioOtro);
    }

    @Test
    void alCambiarDeProveedorElPrecioConservadoSeValidaContraLosLimitesDelNuevo() throws Exception {
        Integer id = crearOrdenNegociada(norte, "92.50");

        // Suministros no negocia: el 92.50 que la linea conserva ya no es admisible.
        editar(id, suministros, linea(tornillo, 10, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("precio_fuera_de_rango"));
    }

    // --- PDF ---

    @Test
    void elPdfDeLaOrdenMuestraElPrecioNegociadoConElDeCatalogoYLaVariacion() throws Exception {
        Integer id = crearOrdenNegociada(norte, "92.50");

        byte[] pdf = mockMvc.perform(get("/api/ordenes/{id}/exportar", id)
                .param("formato", "pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        try (org.apache.pdfbox.pdmodel.PDDocument documento = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String texto = new org.apache.pdfbox.text.PDFTextStripper().getText(documento).replaceAll("\s+", " ");
            assertThat(texto).contains("92.50").contains("catalogo 100.00 (-7.5%)");
        }
    }

    // --- Limites en el CRUD de proveedores ---

    @Test
    void unAdminCreaUnProveedorConLimitesYSinEllosQuedaEnCero() throws Exception {
        mockMvc.perform(post("/api/proveedores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre": "Proveedor Con Margen", "email": "margen@example.com",
                     "descuento_maximo_pct": 12.5, "aumento_maximo_pct": 3}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.descuento_maximo_pct").value(12.5))
            .andExpect(jsonPath("$.aumento_maximo_pct").value(3.0));

        mockMvc.perform(post("/api/proveedores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre": "Proveedor Sin Margen", "email": "sinmargen@example.com"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.descuento_maximo_pct").value(0.0))
            .andExpect(jsonPath("$.aumento_maximo_pct").value(0.0));
    }

    @Test
    void editarUnProveedorSinLosLimitesLosPreservaYConEllosLosCambia() throws Exception {
        mockMvc.perform(put("/api/proveedores/{id}", norte.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre": "Ferretera del Norte S.A.", "email": "contacto@ferreteradelnorte.example.com"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.descuento_maximo_pct").value(10.0))
            .andExpect(jsonPath("$.aumento_maximo_pct").value(5.0));

        mockMvc.perform(put("/api/proveedores/{id}", norte.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre": "Ferretera del Norte S.A.", "email": "contacto@ferreteradelnorte.example.com",
                     "descuento_maximo_pct": 0, "aumento_maximo_pct": 0}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.descuento_maximo_pct").value(0.0));

        // Con 0/0 ya no se puede negociar con ese proveedor.
        crear(norte, linea(tornillo, 1, "95.00")).andExpect(status().isBadRequest());
    }

    @Test
    void losLimitesFueraDeRangoSeRechazan() throws Exception {
        for (String cuerpo : List.of(
            "\"descuento_maximo_pct\": 100", "\"descuento_maximo_pct\": -1",
            "\"aumento_maximo_pct\": 100.01", "\"aumento_maximo_pct\": 5.555")) {
            mockMvc.perform(post("/api/proveedores")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\": \"X\", \"email\": \"x@example.com\", " + cuerpo + "}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void elCompradorVeLosLimitesDeCadaProveedorPeroNoLaClaveDeWebhook() throws Exception {
        mockMvc.perform(get("/api/proveedores").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.nombre == 'Ferretera del Norte S.A.')].descuento_maximo_pct").value(10.0))
            .andExpect(jsonPath("$.data[?(@.nombre == 'Ferretera del Norte S.A.')].aumento_maximo_pct").value(5.0))
            .andExpect(jsonPath("$.data[?(@.nombre == 'Ferretera del Norte S.A.')].webhook_api_key").value((Object) null));
    }

    // --- Importacion por Excel: columna opcional precio_unitario ---

    private MockMultipartFile excel(boolean conColumnaPrecio, Object[]... filas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("Ordenes");
            Row encabezado = hoja.createRow(0);
            int columnas = conColumnaPrecio ? COLUMNAS.length : COLUMNAS.length - 1;
            for (int c = 0; c < columnas; c++) {
                encabezado.createCell(c).setCellValue(COLUMNAS[c]);
            }
            for (int f = 0; f < filas.length; f++) {
                Row fila = hoja.createRow(f + 1);
                for (int c = 0; c < filas[f].length && c < columnas; c++) {
                    Object valor = filas[f][c];
                    if (valor == null) {
                        continue;
                    }
                    if (valor instanceof Number numero) {
                        fila.createCell(c).setCellValue(numero.doubleValue());
                    } else {
                        fila.createCell(c).setCellValue(valor.toString());
                    }
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return new MockMultipartFile("archivo", "ordenes.xlsx", MIME_XLSX, salida.toByteArray());
        }
    }

    private String importar(MockMultipartFile archivo) throws Exception {
        return mockMvc.perform(multipart("/api/ordenes/importaciones")
                .file(archivo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private Object[] filaLote(String lote, Proveedor proveedor, Object cantidad, Object precio) {
        return new Object[] {lote, proveedor.getNombre(), ferreteria.getNombre(), null, LocalDate.now().plusDays(10).toString(),
            "TORN-05", cantidad, precio};
    }

    private double precioDeLaOrden(String respuestaImportacion, String lote) throws Exception {
        List<Integer> ids = JsonPath.read(respuestaImportacion, "$.ordenes_creadas[?(@.referencia_lote == '" + lote + "')].orden_id");
        Integer id = ids.get(0);
        String orden = mockMvc.perform(get("/api/ordenes/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(orden, "$.detalle[0].precio_unitario")).doubleValue();
    }

    @Test
    void laPlantillaIncluyeLaColumnaOpcionalPrecioUnitario() throws Exception {
        byte[] contenido = mockMvc.perform(get("/api/ordenes/importaciones/plantilla")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        try (var libro = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(contenido))) {
            Row encabezado = libro.getSheet("Ordenes").getRow(0);
            assertThat(encabezado.getCell(7).getStringCellValue()).isEqualTo("precio_unitario");
        }
    }

    @Test
    void enLaImportacionUnaCeldaDePrecioVaciaUsaElCatalogoYUnaConPrecioLoNegocia() throws Exception {
        String respuesta = importar(excel(true,
            filaLote("SIN-PRECIO", norte, 10, null),
            filaLote("CON-PRECIO", norte, 10, 95)));

        assertThat(precioDeLaOrden(respuesta, "SIN-PRECIO")).isEqualTo(100.0);
        assertThat(precioDeLaOrden(respuesta, "CON-PRECIO")).isEqualTo(95.0);
    }

    @Test
    void enLaImportacionUnPrecioFueraDeLosLimitesRechazaSoloEseLote() throws Exception {
        mockMvc.perform(multipart("/api/ordenes/importaciones")
                .file(excel(true,
                    filaLote("BUENO", norte, 10, 95),
                    filaLote("MALO", norte, 10, 50),
                    filaLote("SIN-MARGEN", suministros, 10, 99)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ordenes_creadas.length()").value(1))
            .andExpect(jsonPath("$.ordenes_creadas[0].referencia_lote").value("BUENO"))
            .andExpect(jsonPath("$.ordenes_rechazadas.length()").value(2))
            .andExpect(jsonPath("$.ordenes_rechazadas[?(@.referencia_lote == 'MALO')].error.code").value("precio_fuera_de_rango"))
            .andExpect(jsonPath("$.ordenes_rechazadas[?(@.referencia_lote == 'MALO')].error.param").value("precio_unitario"))
            .andExpect(jsonPath("$.ordenes_rechazadas[?(@.referencia_lote == 'SIN-MARGEN')].error.code").value("precio_fuera_de_rango"));
    }

    @Test
    void enLaImportacionUnPrecioNoNumericoSeReportaEnLaColumnaPrecioUnitario() throws Exception {
        mockMvc.perform(multipart("/api/ordenes/importaciones")
                .file(excel(true, filaLote("TEXTO", norte, 10, "barato")))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ordenes_rechazadas[0].error.code").value("formato_invalido"))
            .andExpect(jsonPath("$.ordenes_rechazadas[0].error.param").value("precio_unitario"));
    }

    @Test
    void enLaImportacionElMismoProductoConPreciosDistintosEnUnLoteSeRechaza() throws Exception {
        mockMvc.perform(multipart("/api/ordenes/importaciones")
                .file(excel(true,
                    filaLote("MISMO-LOTE", norte, 5, 95),
                    filaLote("MISMO-LOTE", norte, 5, 96)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenComprador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ordenes_rechazadas[0].error.code").value("precio_inconsistente"));
    }

    @Test
    void enLaImportacionUnMismoProductoConElMismoPrecioSeAgrupaSumandoCantidades() throws Exception {
        String respuesta = importar(excel(true,
            filaLote("AGRUPADO", norte, 5, 95),
            filaLote("AGRUPADO", norte, 5, 95)));

        assertThat(precioDeLaOrden(respuesta, "AGRUPADO")).isEqualTo(95.0);
    }

    @Test
    void unArchivoConLaPlantillaAnteriorSinLaColumnaDePrecioSigueFuncionando() throws Exception {
        String respuesta = importar(excel(false, filaLote("VIEJO", norte, 10, null)));

        assertThat(precioDeLaOrden(respuesta, "VIEJO")).isEqualTo(100.0);
    }
}
