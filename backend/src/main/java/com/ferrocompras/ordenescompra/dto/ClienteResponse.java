package com.ferrocompras.ordenescompra.dto;

import com.ferrocompras.ordenescompra.shared.enums.TipoCliente;
import java.time.Instant;

public record ClienteResponse(
    String object,
    Integer id,
    String nombre,
    TipoCliente tipo,
    String email,
    Boolean activo,
    Instant createdAt,
    Instant updatedAt
) {
}
