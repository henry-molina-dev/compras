package com.ferrocompras.ordenescompra.modulocompras.service;

import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompraDetalle;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Renderiza el PDF de una orden a partir de la entidad ya cargada, dentro de la transaccion de
 * quien lo llama. Vive aparte de ExportacionOrdenesService para que OrdenCompraService tambien
 * pueda generarlo (es el adjunto de los correos de aprobacion y anulacion) sin crear una dependencia
 * circular: ExportacionOrdenesService ya depende de OrdenCompraService para autorizar el acceso.
 */
@Component
public class OrdenCompraPdfRenderer {

    private static final DateTimeFormatter FECHA_LEGIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Devuelve el documento PDF de la orden. Necesita las relaciones lazy de la entidad (proveedor,
     * sucursal, cliente y productos), por lo que debe llamarse con la sesion de Hibernate abierta.
     *
     * @throws IllegalStateException si no se puede generar el documento
     */
    public byte[] renderizar(OrdenCompra orden) {
        return renderizarPdf(construirHtml(orden));
    }

    private List<OrdenCompraDetalle> ordenadoPorId(OrdenCompra orden) {
        return orden.getDetalle().stream().sorted(Comparator.comparing(OrdenCompraDetalle::getId)).toList();
    }

    private String construirHtml(OrdenCompra orden) {
        StringBuilder filas = new StringBuilder();
        for (OrdenCompraDetalle linea : ordenadoPorId(orden)) {
            filas.append("""
                <tr>
                  <td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>
                </tr>
                """.formatted(
                    escapar(linea.getProducto().getCodigo()),
                    escapar(linea.getProducto().getNombre()),
                    linea.getCantidad().toPlainString(),
                    celdaPrecio(linea),
                    moneda(linea.getSubtotal())));
        }

        String filaCliente = orden.getCliente() == null ? "" : """
            <tr><td class="label">Cliente</td><td>%s</td></tr>
            """.formatted(escapar(orden.getCliente().getNombre()));
        // Misma paleta y tipografia que el frontend (mismos valores hexadecimales), para que el
        // documento se vea consistente con el resto de la aplicacion.
        return """
            <html>
            <head>
            <style>
              @page { size: A4; margin: 2cm; }
              body { font-family: "Inter", sans-serif; font-weight: 400; font-size: 13px; color: #292524; }
              h1 { font-size: 20px; font-weight: 500; color: #194F90; margin: 0 0 4px 0; }
              .meta { color: #78716C; font-size: 12px; margin-bottom: 20px; }
              .cabecera table { width: 100%%; border-collapse: collapse; margin-bottom: 20px; }
              .cabecera td { padding: 4px 0; font-size: 13px; }
              .cabecera td.label { color: #78716C; width: 160px; }
              table.detalle { width: 100%%; border-collapse: collapse; }
              table.detalle th { text-align: left; font-weight: 500; font-size: 12px; color: #78716C;
                border-bottom: 1px solid #E7E5E4; padding: 6px 4px; }
              table.detalle td { font-size: 13px; padding: 6px 4px; border-bottom: 1px solid #E7E5E4; }
              .total { text-align: right; margin-top: 16px; font-size: 20px; font-weight: 500; }
            </style>
            </head>
            <body>
              <h1>Orden de compra %s</h1>
              <div class="meta">Estado: %s</div>
              <div class="cabecera">
                <table>
                  <tr><td class="label">Proveedor</td><td>%s</td></tr>
                  <tr><td class="label">Sucursal destino</td><td>%s</td></tr>
                  %s
                  <tr><td class="label">Fecha necesaria</td><td>%s</td></tr>
                </table>
              </div>
              <table class="detalle">
                <thead>
                  <tr><th>Codigo</th><th>Producto</th><th>Cantidad</th><th>Precio unitario</th><th>Subtotal</th></tr>
                </thead>
                <tbody>
                  %s
                </tbody>
              </table>
              <div class="total">Total: %s</div>
            </body>
            </html>
            """.formatted(
                escapar(orden.getNumeroOrden()), orden.getEstado(),
                escapar(orden.getProveedor().getNombre()), escapar(orden.getSucursalDestino().getNombre()),
                filaCliente, orden.getFechaNecesaria().format(FECHA_LEGIBLE),
                filas, moneda(orden.getTotal()));
    }

    // Precio unitario de la linea; si se negocio, debajo se anota el de catalogo y la variacion.
    private String celdaPrecio(OrdenCompraDetalle linea) {
        String precio = moneda(linea.getPrecioUnitario());
        BigDecimal catalogo = linea.getPrecioCatalogo();
        if (catalogo == null || linea.getPrecioUnitario().compareTo(catalogo) == 0) {
            return precio;
        }
        BigDecimal variacion = linea.getPrecioUnitario().subtract(catalogo)
            .multiply(BigDecimal.valueOf(100)).divide(catalogo, 1, RoundingMode.HALF_UP);
        String signo = variacion.signum() > 0 ? "+" : "";
        return precio + "<br/><span style=\"font-size: 11px; color: #78716C;\">catalogo " + moneda(catalogo)
            + " (" + signo + variacion.toPlainString() + "%)</span>";
    }

    private String moneda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private byte[] renderizarPdf(String html) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // Registrada por InputStream (classpath), no por File: el jar/classpath del backend no
            // garantiza una ruta de archivo real en disco. "Inter" solo declara dos pesos (400/500)
            // por diseno de la interfaz (se evitan las negritas fuertes).
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/Inter-Regular.ttf"),
                "Inter", 400, FontStyle.NORMAL, true);
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/Inter-Medium.ttf"),
                "Inter", 500, FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(salida);
            builder.run();
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el PDF de la orden.", ex);
        }
    }
}
