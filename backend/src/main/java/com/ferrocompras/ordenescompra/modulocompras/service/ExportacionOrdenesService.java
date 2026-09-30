package com.ferrocompras.ordenescompra.modulocompras.service;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompraDetalle;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraRepository;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import com.ferrocompras.ordenescompra.shared.enums.Rol;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exporta ordenes en dos formatos con granularidad propia: el PDF de una orden y un reporte Excel
 * con una fila por linea de producto.
 *
 * <p>Ambos leen directamente de las entidades, no del DTO de la API, porque necesitan datos
 * denormalizados (nombre de proveedor, sucursal, cliente y producto) que el listado JSON no expone
 * por defecto: incluirlos alli seria traer de mas para quien no los necesita.
 */
@Service
public class ExportacionOrdenesService {

    private final OrdenCompraService ordenCompraService;
    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraPdfRenderer pdfRenderer;

    public ExportacionOrdenesService(
        OrdenCompraService ordenCompraService, OrdenCompraRepository ordenCompraRepository, OrdenCompraPdfRenderer pdfRenderer
    ) {
        this.ordenCompraService = ordenCompraService;
        this.ordenCompraRepository = ordenCompraRepository;
        this.pdfRenderer = pdfRenderer;
    }

    public record PdfExportado(String numeroOrden, byte[] contenido) {
    }

    /**
     * Genera el PDF de una orden, con el mismo control de acceso que consultarla.
     *
     * @throws org.springframework.security.access.AccessDeniedException si un GERENTE_SUCURSAL pide una
     *     orden de otra sucursal
     * @throws com.ferrocompras.ordenescompra.exception.RecursoNoEncontradoException si la orden no existe
     */
    @Transactional(readOnly = true)
    public PdfExportado exportarPdf(Integer id, UsuarioPrincipal actor) {
        OrdenCompra orden = ordenCompraService.obtenerOrdenEntidadAutorizada(id, actor);
        return new PdfExportado(orden.getNumeroOrden(), pdfRenderer.renderizar(orden));
    }

    private static final String[] COLUMNAS_LISTADO = {
        "numero_orden", "proveedor", "sucursal_destino", "cliente", "estado", "fecha_necesaria",
        "producto_codigo", "producto_nombre", "cantidad", "precio_unitario", "subtotal", "total_orden"
    };

    /**
     * Genera el reporte Excel de las ordenes que cumplen los filtros: una fila por linea de producto,
     * con los datos de la orden repetidos. No pagina: exporta todo lo que coincida.
     *
     * <p>Un GERENTE_SUCURSAL queda restringido a su sucursal sin importar el filtro que envie.
     *
     * @throws com.ferrocompras.ordenescompra.exception.NegocioException si el rango de fechas esta invertido
     */
    @Transactional(readOnly = true)
    public byte[] exportarExcel(
        EstadoOrden estado, Integer proveedorId, Integer sucursalId, Integer clienteId, String numeroOrden,
        LocalDate fechaDesde, LocalDate fechaHasta, UsuarioPrincipal actor
    ) {
        OrdenCompraService.validarRangoFechas(fechaDesde, fechaHasta);

        // Mismo criterio que OrdenCompraService.listar(): un GERENTE_SUCURSAL nunca exporta otra
        // sucursal aunque la pida por parametro.
        Integer sucursalEfectiva = actor.rol() == Rol.GERENTE_SUCURSAL ? actor.sucursalId() : sucursalId;
        List<OrdenCompra> ordenes = ordenCompraRepository.buscar(estado, proveedorId, sucursalEfectiva, clienteId,
            numeroOrden, OrdenCompraService.sinFiltroDesde(fechaDesde), OrdenCompraService.sinFiltroHasta(fechaHasta),
            null, Pageable.unpaged());
        return construirExcel(ordenes);
    }

    private byte[] construirExcel(List<OrdenCompra> ordenes) {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Ordenes");

            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFont(negrita);
            estiloEncabezado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row encabezado = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS_LISTADO.length; c++) {
                Cell celda = encabezado.createCell(c);
                celda.setCellValue(COLUMNAS_LISTADO[c]);
                celda.setCellStyle(estiloEncabezado);
                hoja.setColumnWidth(c, 20 * 256);
            }

            int f = 1;
            for (OrdenCompra orden : ordenes) {
                for (OrdenCompraDetalle linea : ordenadoPorId(orden)) {
                    Row fila = hoja.createRow(f++);
                    fila.createCell(0).setCellValue(orden.getNumeroOrden());
                    fila.createCell(1).setCellValue(orden.getProveedor().getNombre());
                    fila.createCell(2).setCellValue(orden.getSucursalDestino().getNombre());
                    if (orden.getCliente() != null) {
                        fila.createCell(3).setCellValue(orden.getCliente().getNombre());
                    }
                    fila.createCell(4).setCellValue(orden.getEstado().name());
                    fila.createCell(5).setCellValue(orden.getFechaNecesaria().toString());
                    fila.createCell(6).setCellValue(linea.getProducto().getCodigo());
                    fila.createCell(7).setCellValue(linea.getProducto().getNombre());
                    fila.createCell(8).setCellValue(linea.getCantidad().doubleValue());
                    fila.createCell(9).setCellValue(linea.getPrecioUnitario().doubleValue());
                    fila.createCell(10).setCellValue(linea.getSubtotal().doubleValue());
                    fila.createCell(11).setCellValue(orden.getTotal().doubleValue());
                }
            }

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el reporte de ordenes.", ex);
        }
    }

    private List<OrdenCompraDetalle> ordenadoPorId(OrdenCompra orden) {
        return orden.getDetalle().stream().sorted(Comparator.comparing(OrdenCompraDetalle::getId)).toList();
    }
}
