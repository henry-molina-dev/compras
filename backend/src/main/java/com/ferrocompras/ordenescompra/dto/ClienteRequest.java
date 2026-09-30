package com.ferrocompras.ordenescompra.dto;

import com.ferrocompras.ordenescompra.shared.enums.TipoCliente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
    @NotBlank @Size(max = 150) String nombre,
    @NotNull TipoCliente tipo,
    @Email String email,
    // Opcional: mismo criterio que ProveedorRequest.activo.
    Boolean activo
) {
}
