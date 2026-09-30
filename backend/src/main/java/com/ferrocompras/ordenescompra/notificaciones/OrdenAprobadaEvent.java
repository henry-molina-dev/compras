package com.ferrocompras.ordenescompra.notificaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Datos ya resueltos (no referencias a entidades): el listener corre async, despues del commit,
// cuando la sesion de Hibernate que cargo la orden ya no existe. El PDF tambien se renderiza antes
// de publicar el evento, por la misma razon (necesita las relaciones de la entidad todavia vivas).
public record OrdenAprobadaEvent(
    String numeroOrden,
    String proveedorEmail,
    String proveedorNombre,
    LocalDate fechaNecesaria,
    BigDecimal total,
    List<Linea> detalle,
    byte[] pdfOrden
) {

    public record Linea(String productoNombre, BigDecimal cantidad, String unidadCompra) {
    }
}
