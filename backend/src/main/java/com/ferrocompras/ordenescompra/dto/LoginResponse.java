package com.ferrocompras.ordenescompra.dto;

import com.ferrocompras.ordenescompra.shared.enums.Rol;

public record LoginResponse(
    String object,
    String accessToken,
    String tokenType,
    long expiresIn,
    Rol rol
) {
}
