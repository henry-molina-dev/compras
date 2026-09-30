package com.ferrocompras.ordenescompra.dto;

import com.ferrocompras.ordenescompra.shared.enums.FormatoSucursal;
import java.time.Instant;

public record SucursalResponse(
    String object,
    Integer id,
    String nombre,
    FormatoSucursal formato,
    Boolean activo,
    Instant createdAt,
    Instant updatedAt
) {
}
