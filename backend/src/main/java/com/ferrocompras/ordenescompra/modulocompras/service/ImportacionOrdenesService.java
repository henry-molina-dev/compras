package com.ferrocompras.ordenescompra.modulocompras.service;

import com.ferrocompras.ordenescompra.dto.DetalleRequest;
import com.ferrocompras.ordenescompra.dto.ErrorResponse;
import com.ferrocompras.ordenescompra.dto.ImportacionLoteResponse;
import com.ferrocompras.ordenescompra.dto.OrdenCompraRequest;
import com.ferrocompras.ordenescompra.dto.OrdenCompraResponse;
import com.ferrocompras.ordenescompra.dto.OrdenCreadaLoteResponse;
import com.ferrocompras.ordenescompra.dto.OrdenRechazadaLoteResponse;
import com.ferrocompras.ordenescompra.exception.NegocioException;
import com.ferrocompras.ordenescompra.exception.RecursoNoEncontradoException;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import com.ferrocompras.ordenescompra.shared.entity.Cliente;
import com.ferrocompras.ordenescompra.shared.entity.Producto;
import com.ferrocompras.ordenescompra.shared.entity.Proveedor;
import com.ferrocompras.ordenescompra.shared.entity.Sucursal;
import com.ferrocompras.ordenescompra.shared.repository.ClienteRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProductoRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProveedorRepository;
import com.ferrocompras.ordenescompra.shared.repository.SucursalRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Carga masiva: agrupa las filas de la hoja "Ordenes" por referencia_lote, arma el mismo
 * OrdenCompraRequest que el formulario individual y llama al mismo OrdenCompraService.crearOrden()
 * - ninguna regla de negocio (formato/categoria, cliente obligatorio, totales) se duplica aqui.
 *
 * <p>Se usa .xlsx (Apache POI) en vez de CSV de texto plano: un CSV depende del separador de lista de
 * la configuracion regional del sistema operativo de quien lo edita en Excel/LibreOffice (en
 * locales donde la coma es el separador decimal, exportan ";" en vez de ",", y el numero/fecha
 * tambien se escriben en el formato regional en vez de un formato neutral) - un archivo .xlsx no
 * tiene ese problema porque cada celda es un valor tipado (numero o fecha reales, no texto a
 * interpretar). Ademas permite ofrecer hojas de referencia con listas desplegables para
 * proveedor/sucursal/cliente/producto, que el usuario no tiene forma de conocer como IDs.
 *
 * <p>Atomicidad por orden, no por archivo: esta clase deliberadamente NO es @Transactional. Cada
 * llamada a crearOrden() (que si es @Transactional, con propagacion REQUIRED por defecto) abre y
 * cierra su propia transaccion porque no hay ninguna transaccion ya activa alrededor del bucle. Si
 * este metodo tuviera @Transactional, una excepcion de negocio en un lote marcaria toda la
 * transaccion compartida como rollback-only y arrastraria consigo los lotes ya creados con exito
 * en el mismo archivo - justo lo que este servicio debe evitar.
 */
@Service
public class ImportacionOrdenesService {

    private static final String HOJA_ORDENES = "Ordenes";
    private static final String HOJA_PROVEEDORES = "Proveedores";
    private static final String HOJA_SUCURSALES = "Sucursales";
    private static final String HOJA_CLIENTES = "Clientes";
    private static final String HOJA_PRODUCTOS = "Productos";

    private static final List<String> COLUMNAS_ORDENES = List.of(
        "referencia_lote", "proveedor", "sucursal_destino", "cliente", "fecha_necesaria",
        "producto_codigo", "cantidad", "precio_unitario");

    private final OrdenCompraService ordenCompraService;
    private final ProveedorRepository proveedorRepository;
    private final SucursalRepository sucursalRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final Validator validator;

    public ImportacionOrdenesService(
        OrdenCompraService ordenCompraService,
        ProveedorRepository proveedorRepository,
        SucursalRepository sucursalRepository,
        ClienteRepository clienteRepository,
        ProductoRepository productoRepository,
        Validator validator
    ) {
        this.ordenCompraService = ordenCompraService;
        this.proveedorRepository = proveedorRepository;
        this.sucursalRepository = sucursalRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.validator = validator;
    }

    /**
     * Genera el Excel de la plantilla de carga masiva: la hoja "Ordenes" con las columnas que espera
     * {@link #importar} y una fila de ejemplo, mas hojas de referencia (proveedores, sucursales,
     * clientes y productos activos) que alimentan listas desplegables.
     */
    public byte[] generarPlantilla() {

        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            List<String> proveedores = proveedorRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(Proveedor::getNombre).toList();
            List<String> sucursales = sucursalRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(Sucursal::getNombre).toList();
            List<String> clientes = clienteRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(Cliente::getNombre).toList();
            List<String> productos = productoRepository.findByActivoTrueOrderByCodigoAsc().stream()
                .map(p -> p.getCodigo() + " - " + p.getNombre()).toList();

            Sheet hojaOrdenes = crearHojaOrdenesConEjemplo(libro, proveedores, sucursales, productos);
            crearHojaReferencia(libro, HOJA_PROVEEDORES, "Proveedor", proveedores);
            crearHojaReferencia(libro, HOJA_SUCURSALES, "Sucursal", sucursales);
            crearHojaReferencia(libro, HOJA_CLIENTES, "Cliente", clientes);
            crearHojaReferencia(libro, HOJA_PRODUCTOS, "Producto", productos);

            agregarListaDesplegable(libro, hojaOrdenes, 1, HOJA_PROVEEDORES, proveedores.size());
            agregarListaDesplegable(libro, hojaOrdenes, 2, HOJA_SUCURSALES, sucursales.size());
            agregarListaDesplegable(libro, hojaOrdenes, 3, HOJA_CLIENTES, clientes.size());
            agregarListaDesplegable(libro, hojaOrdenes, 5, HOJA_PRODUCTOS, productos.size());

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new NegocioException("plantilla_invalida", "No se pudo generar la plantilla de importacion.", null);
        }
    }

    private Sheet crearHojaOrdenesConEjemplo(
        Workbook libro, List<String> proveedores, List<String> sucursales, List<String> productos
    ) {
        Sheet hoja = libro.createSheet(HOJA_ORDENES);
        Font negrita = libro.createFont();
        negrita.setBold(true);
        var estiloEncabezado = libro.createCellStyle();
        estiloEncabezado.setFont(negrita);
        estiloEncabezado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row encabezado = hoja.createRow(0);
        for (int i = 0; i < COLUMNAS_ORDENES.size(); i++) {
            Cell celda = encabezado.createCell(i);
            celda.setCellValue(COLUMNAS_ORDENES.get(i));
            celda.setCellStyle(estiloEncabezado);
        }

        if (!proveedores.isEmpty() && !sucursales.isEmpty() && !productos.isEmpty()) {
            Row ejemplo = hoja.createRow(1);
            ejemplo.createCell(0).setCellValue("LOTE-1");
            ejemplo.createCell(1).setCellValue(proveedores.get(0));
            ejemplo.createCell(2).setCellValue(sucursales.get(0));
            ejemplo.createCell(4).setCellValue(LocalDate.now().plusDays(15).toString());
            ejemplo.createCell(5).setCellValue(productos.get(0));
            ejemplo.createCell(6).setCellValue(10);
        }

        for (int i = 0; i < COLUMNAS_ORDENES.size(); i++) {
            hoja.setColumnWidth(i, 28 * 256);
        }
        return hoja;
    }

    private void crearHojaReferencia(Workbook libro, String nombreHoja, String titulo, List<String> valores) {
        Sheet hoja = libro.createSheet(nombreHoja);
        Row encabezado = hoja.createRow(0);
        encabezado.createCell(0).setCellValue(titulo);
        for (int i = 0; i < valores.size(); i++) {
            hoja.createRow(i + 1).createCell(0).setCellValue(valores.get(i));
        }
        hoja.setColumnWidth(0, 32 * 256);
    }

    // Las hojas de referencia solo existen para alimentar estas listas desplegables: el usuario
    // elige un valor en vez de tener que conocer/escribir un ID o un nombre exacto de memoria.
    private void agregarListaDesplegable(Workbook libro, Sheet hojaOrdenes, int columna, String hojaReferencia, int cantidadValores) {
        DataValidationHelper helper = hojaOrdenes.getDataValidationHelper();
        int ultimaFila = Math.max(cantidadValores + 1, 2);
        String formula = "'" + hojaReferencia + "'!$A$2:$A$" + ultimaFila;
        DataValidationConstraint restriccion = helper.createFormulaListConstraint(formula);
        CellRangeAddressList rango = new CellRangeAddressList(1, 1000, columna, columna);
        DataValidation validacion = helper.createValidation(restriccion, rango);
        validacion.setSuppressDropDownArrow(true);
        validacion.setShowErrorBox(true);
        validacion.setEmptyCellAllowed(true);
        hojaOrdenes.addValidationData(validacion);
    }

    /**
     * Procesa un archivo de carga masiva: agrupa las filas por referencia de lote y crea una orden por
     * lote, con las mismas reglas que el flujo individual.
     *
     * <p>La atomicidad es por orden, no por archivo: un lote rechazado no afecta a los que ya se
     * crearon. La respuesta informa cuales se crearon y cuales se rechazaron, con el motivo y la
     * columna del archivo a la que se refiere.
     *
     * @throws com.ferrocompras.ordenescompra.exception.NegocioException si el archivo no es un .xlsx
     *     valido o le falta una columna obligatoria
     */
    public ImportacionLoteResponse importar(
MultipartFile archivo, UsuarioPrincipal actor) {
        Map<String, List<Row>> lotes = agruparPorLote(archivo);

        List<OrdenCreadaLoteResponse> creadas = new ArrayList<>();
        List<OrdenRechazadaLoteResponse> rechazadas = new ArrayList<>();

        for (Map.Entry<String, List<Row>> lote : lotes.entrySet()) {
            String referencia = lote.getKey();
            try {
                OrdenCompraRequest request = construirRequest(lote.getValue());
                validarOLanzar(request);
                OrdenCompraResponse creada = ordenCompraService.crearOrden(request, actor);
                creadas.add(new OrdenCreadaLoteResponse(referencia, creada.id(), creada.numeroOrden()));
            } catch (NegocioException ex) {
                rechazadas.add(rechazo(referencia, ex.getCode(), ex.getMessage(), ex.getParam()));
            } catch (RecursoNoEncontradoException ex) {
                rechazadas.add(rechazo(referencia, ex.getCode(), ex.getMessage(), ex.getParam()));
            }
        }

        return ImportacionLoteResponse.of(creadas, rechazadas);
    }

    private OrdenRechazadaLoteResponse rechazo(String referencia, String code, String message, String param) {
        // El flujo individual identifica una linea con "detalle[i].producto_id" (indice de un
        // array JSON que aqui no existe); en el reporte de carga masiva se traduce a la columna
        // real de la hoja, que es lo que el usuario ve en su archivo.
        String paramHoja = param;
        if (param != null && param.startsWith("detalle[")) {
            paramHoja = param.endsWith(".precio_unitario") ? "precio_unitario" : "producto_codigo";
        }
        return new OrdenRechazadaLoteResponse(referencia,
            new ErrorResponse.ErrorDetail("invalid_request_error", code, message, paramHoja));
    }

    private void validarOLanzar(OrdenCompraRequest request) {
        Set<ConstraintViolation<OrdenCompraRequest>> violaciones = validator.validate(request);
        if (!violaciones.isEmpty()) {
            ConstraintViolation<OrdenCompraRequest> primera = violaciones.iterator().next();
            throw new NegocioException("validacion", primera.getMessage(), traducirPropiedad(primera));
        }
    }

    private String traducirPropiedad(ConstraintViolation<OrdenCompraRequest> violacion) {
        String path = violacion.getPropertyPath().toString();
        if (path.equals("fechaNecesaria")) {
            return "fecha_necesaria";
        }
        if (path.startsWith("detalle")) {
            return path.endsWith("precioUnitario") ? "precio_unitario" : "cantidad";
        }
        return path;
    }

    private OrdenCompraRequest construirRequest(List<Row> filas) {
        Map<String, Integer> indices = indicesColumnas(filas.get(0).getSheet());
        Row cabecera = filas.get(0);

        Integer proveedorId = resolverProveedor(obtenerTexto(cabecera, indices, "proveedor"));
        Integer sucursalDestinoId = resolverSucursal(obtenerTexto(cabecera, indices, "sucursal_destino"));
        Integer clienteId = resolverClienteOpcional(obtenerTexto(cabecera, indices, "cliente"));
        LocalDate fechaNecesaria = obtenerFecha(cabecera, indices, "fecha_necesaria");
        List<DetalleRequest> detalle = agruparPorProducto(filas.stream().map(f -> construirDetalle(f, indices)).toList());

        return new OrdenCompraRequest(proveedorId, sucursalDestinoId, clienteId, fechaNecesaria, detalle);
    }

    // El archivo no impide que el mismo producto aparezca en varias filas del lote (a diferencia
    // del formulario individual, que ya lo evita en la UI); se agrupan sumando cantidades antes de
    // armar el request para no guardar lineas duplicadas del mismo producto en una orden.
    // Si el mismo producto trae precios distintos en sus filas (o precio en una y vacio en otra) no hay
    // un precio unico que aplicar a la linea: se rechaza el lote en vez de elegir uno al azar.
    private List<DetalleRequest> agruparPorProducto(List<DetalleRequest> detalle) {
        Map<Integer, DetalleRequest> porProducto = new LinkedHashMap<>();
        for (DetalleRequest linea : detalle) {
            DetalleRequest previa = porProducto.get(linea.productoId());
            if (previa == null) {
                porProducto.put(linea.productoId(), linea);
                continue;
            }
            if (!mismoPrecio(previa.precioUnitario(), linea.precioUnitario())) {
                throw new NegocioException("precio_inconsistente",
                    "Un producto aparece en varias filas del lote con precios distintos; usa el mismo precio (o ninguno) en todas.",
                    "precio_unitario");
            }
            porProducto.put(linea.productoId(),
                new DetalleRequest(linea.productoId(), previa.cantidad().add(linea.cantidad()), previa.precioUnitario()));
        }
        return List.copyOf(porProducto.values());
    }

    private boolean mismoPrecio(BigDecimal a, BigDecimal b) {
        return (a == null && b == null) || (a != null && b != null && a.compareTo(b) == 0);
    }

    private DetalleRequest construirDetalle(Row fila, Map<String, Integer> indices) {
        String valorProducto = obtenerTexto(fila, indices, "producto_codigo");
        // La hoja de referencia "Productos" muestra "CODIGO - Nombre" para que el usuario reconozca
        // el producto sin memorizar el codigo; solo la parte antes de " - " es el codigo real.
        String codigo = valorProducto == null ? null : valorProducto.split(" - ", 2)[0].trim();
        Producto producto = productoRepository.findByCodigo(codigo)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "producto_no_encontrado", "No existe el producto con codigo " + codigo + ".", "producto_codigo"));
        BigDecimal cantidad = obtenerCantidad(fila, indices, "cantidad");
        return new DetalleRequest(producto.getId(), cantidad, obtenerPrecioOpcional(fila, indices, "precio_unitario"));
    }

    private Integer resolverProveedor(String nombre) {
        List<Proveedor> encontrados = proveedorRepository.findByNombreIgnoreCase(requerido(nombre, "proveedor"));
        return unicoOLanzar(encontrados, Proveedor::getId, "proveedor_no_encontrado", "proveedor", nombre);
    }

    private Integer resolverSucursal(String nombre) {
        List<Sucursal> encontradas = sucursalRepository.findByNombreIgnoreCase(requerido(nombre, "sucursal_destino"));
        return unicoOLanzar(encontradas, Sucursal::getId, "sucursal_no_encontrada", "sucursal_destino", nombre);
    }

    private Integer resolverClienteOpcional(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        List<Cliente> encontrados = clienteRepository.findByNombreIgnoreCase(nombre);
        return unicoOLanzar(encontrados, Cliente::getId, "cliente_no_encontrado", "cliente", nombre);
    }

    private <T> Integer unicoOLanzar(List<T> encontrados, java.util.function.Function<T, Integer> id, String codigo, String columna, String nombre) {
        if (encontrados.isEmpty()) {
            throw new RecursoNoEncontradoException(codigo, "No existe " + columna + " con nombre '" + nombre + "'.", columna);
        }
        if (encontrados.size() > 1) {
            throw new NegocioException("nombre_ambiguo",
                "Hay mas de un/a " + columna + " con el nombre '" + nombre + "'. Corrige el catalogo o usa un nombre exacto.",
                columna);
        }
        return id.apply(encontrados.get(0));
    }

    private String requerido(String valor, String columna) {
        if (valor == null || valor.isBlank()) {
            throw new NegocioException("formato_invalido", "La columna '" + columna + "' es obligatoria.", columna);
        }
        return valor;
    }

    private Map<String, List<Row>> agruparPorLote(MultipartFile archivo) {
        try (Workbook libro = WorkbookFactory.create(archivo.getInputStream())) {
            Sheet hoja = libro.getSheet(HOJA_ORDENES);
            if (hoja == null) {
                throw new NegocioException("archivo_invalido",
                    "El archivo no tiene una hoja llamada '" + HOJA_ORDENES + "'. Descarga la plantilla e intenta de nuevo.",
                    "archivo");
            }
            Map<String, Integer> indices = indicesColumnas(hoja);

            Map<String, List<Row>> lotes = new LinkedHashMap<>();
            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null || esFilaVacia(fila)) {
                    continue;
                }
                String referenciaLote = requerido(obtenerTexto(fila, indices, "referencia_lote"), "referencia_lote");
                lotes.computeIfAbsent(referenciaLote, k -> new ArrayList<>()).add(fila);
            }
            return lotes;
        } catch (IOException | org.apache.poi.EmptyFileException ex) {
            throw new NegocioException("archivo_invalido",
                "No se pudo leer el archivo. Debe ser un archivo Excel (.xlsx) valido, generado con la plantilla.",
                "archivo");
        }
    }

    private boolean esFilaVacia(Row fila) {
        for (Cell celda : fila) {
            if (celda.getCellType() != CellType.BLANK && !obtenerValorGenerico(celda).isBlank()) {
                return false;
            }
        }
        return true;
    }

    // Construye el mapa nombre-de-columna -> indice leyendo el encabezado real de la hoja en vez de
    // asumir un orden fijo, para que reordenar columnas en el archivo no rompa la carga - y para dar
    // un error claro (no una excepcion tecnica sin capturar) cuando falta una columna esperada.
    private Map<String, Integer> indicesColumnas(Sheet hoja) {
        Row encabezado = hoja.getRow(0);
        if (encabezado == null) {
            throw new NegocioException("archivo_invalido", "La hoja '" + HOJA_ORDENES + "' no tiene encabezado.", "archivo");
        }
        Map<String, Integer> indices = new LinkedHashMap<>();
        for (Cell celda : encabezado) {
            String nombre = obtenerValorGenerico(celda).trim().toLowerCase();
            if (!nombre.isEmpty()) {
                indices.put(nombre, celda.getColumnIndex());
            }
        }
        for (String columna : COLUMNAS_ORDENES) {
            if (columna.equals("cliente") || columna.equals("precio_unitario")) {
                continue;
            }
            if (!indices.containsKey(columna)) {
                throw new NegocioException("archivo_invalido",
                    "El archivo no tiene la columna '" + columna + "'. Descarga la plantilla mas reciente e intenta de nuevo.",
                    "archivo");
            }
        }
        return indices;
    }

    private String obtenerTexto(Row fila, Map<String, Integer> indices, String columna) {
        Integer idx = indices.get(columna);
        if (idx == null) {
            return null;
        }
        Cell celda = fila.getCell(idx);
        String valor = obtenerValorGenerico(celda);
        return valor.isBlank() ? null : valor.trim();
    }

    private String obtenerValorGenerico(Cell celda) {
        if (celda == null) {
            return "";
        }
        return switch (celda.getCellType()) {
            case STRING -> celda.getStringCellValue();
            case BOOLEAN -> String.valueOf(celda.getBooleanCellValue());
            case NUMERIC -> {
                double valor = celda.getNumericCellValue();
                yield (valor == Math.floor(valor) && !Double.isInfinite(valor))
                    ? String.valueOf((long) valor) : String.valueOf(valor);
            }
            case FORMULA -> celda.getCellFormula();
            default -> "";
        };
    }

    private LocalDate obtenerFecha(Row fila, Map<String, Integer> indices, String columna) {
        Integer idx = indices.get(columna);
        Cell celda = idx == null ? null : fila.getCell(idx);
        if (celda == null || celda.getCellType() == CellType.BLANK) {
            throw new NegocioException("formato_invalido", "La columna '" + columna + "' es obligatoria.", columna);
        }
        if (celda.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(celda)) {
            return celda.getLocalDateTimeCellValue().toLocalDate();
        }
        // Celda de texto (el usuario escribio la fecha en vez de usar una celda de tipo fecha): solo
        // se acepta el formato ISO, sin ambiguedad dia/mes - adivinar mal aqui crearia una orden con
        // la fecha equivocada sin que nadie lo note.
        try {
            return LocalDate.parse(obtenerValorGenerico(celda).trim());
        } catch (DateTimeParseException ex) {
            throw new NegocioException("formato_invalido",
                columna + " debe ser una celda de fecha, o texto en formato YYYY-MM-DD.", columna);
        }
    }

    private BigDecimal obtenerCantidad(Row fila, Map<String, Integer> indices, String columna) {
        Integer idx = indices.get(columna);
        Cell celda = idx == null ? null : fila.getCell(idx);
        if (celda == null || celda.getCellType() == CellType.BLANK) {
            throw new NegocioException("formato_invalido", columna + " es obligatoria.", columna);
        }
        if (celda.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(celda.getNumericCellValue());
        }
        // Celda de texto: se tolera coma decimal (p. ej. "1,5") ademas de punto, ya que es el
        // formato que una hoja de calculo en espanol usa al mostrarle el numero al usuario.
        try {
            return new BigDecimal(obtenerValorGenerico(celda).trim().replace(",", "."));
        } catch (NumberFormatException ex) {
            throw new NegocioException("formato_invalido", columna + " debe ser un numero.", columna);
        }
    }

    // Columna opcional: celda vacia (o columna ausente en archivos viejos) = precio de catalogo.
    private BigDecimal obtenerPrecioOpcional(Row fila, Map<String, Integer> indices, String columna) {
        Integer idx = indices.get(columna);
        Cell celda = idx == null ? null : fila.getCell(idx);
        if (celda == null || celda.getCellType() == CellType.BLANK || obtenerValorGenerico(celda).isBlank()) {
            return null;
        }
        if (celda.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(celda.getNumericCellValue());
        }
        try {
            return new BigDecimal(obtenerValorGenerico(celda).trim().replace(",", "."));
        } catch (NumberFormatException ex) {
            throw new NegocioException("formato_invalido", columna + " debe ser un numero.", columna);
        }
    }
}
