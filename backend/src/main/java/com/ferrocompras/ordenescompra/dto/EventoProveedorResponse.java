package com.ferrocompras.ordenescompra.dto;

import com.ferrocompras.ordenescompra.modulocompras.entity.TipoEventoProveedor;
import java.time.Instant;

public record EventoProveedorResponse(
    String object,
    Integer id,
    Integer ordenCompraId,
    TipoEventoProveedor tipoEvento,
    String observacion,
    Instant fechaEvento,
    Instant createdAt
) {
}
