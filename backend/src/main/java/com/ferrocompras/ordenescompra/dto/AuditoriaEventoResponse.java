package com.ferrocompras.ordenescompra.dto;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import java.time.Instant;

public record AuditoriaEventoResponse(
    String object,
    Integer id,
    Integer ordenCompraId,
    EstadoOrden estadoAnterior,
    EstadoOrden estadoNuevo,
    Integer usuarioId,
    String observacion,
    Instant fecha
) {
}
