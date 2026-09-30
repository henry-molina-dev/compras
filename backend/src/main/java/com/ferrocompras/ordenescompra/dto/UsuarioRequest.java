package com.ferrocompras.ordenescompra.dto;

import com.ferrocompras.ordenescompra.shared.enums.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
    @NotBlank @Size(min = 3, max = 60) String username,
    // Obligatorio al crear (lo valida el servicio); en edicion, si se omite se conserva la actual.
    @Size(min = 8) String password,
    @NotNull Rol rol,
    Integer sucursalId,
    // Opcional: mismo criterio que ProveedorRequest.activo.
    Boolean activo
) {
}
