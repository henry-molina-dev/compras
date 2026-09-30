package com.ferrocompras.ordenescompra.dto;

import java.time.Instant;

public record LineaTiempoItemResponse(
    String origen,
    String tipo,
    String observacion,
    Instant fecha
) {
}
