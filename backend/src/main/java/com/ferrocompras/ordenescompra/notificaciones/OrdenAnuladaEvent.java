package com.ferrocompras.ordenescompra.notificaciones;

import java.time.Instant;

// El PDF se renderiza antes de publicar el evento, mientras la entidad todavia esta cargada dentro
// de la transaccion que anulo la orden (ver comentario de OrdenAprobadaEvent).
public record OrdenAnuladaEvent(
    String numeroOrden,
    String proveedorEmail,
    String proveedorNombre,
    String motivoAnulacion,
    Instant fechaAnulacion,
    byte[] pdfOrden
) {
}
