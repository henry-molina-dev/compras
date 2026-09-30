package com.ferrocompras.ordenescompra.dto;

import com.ferrocompras.ordenescompra.shared.enums.Rol;
import java.time.Instant;

public record UsuarioResponse(
    String object,
    Integer id,
    String username,
    Rol rol,
    Integer sucursalId,
    Boolean activo,
    Instant createdAt,
    Instant updatedAt
) {
}
