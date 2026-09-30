package com.ferrocompras.ordenescompra.dto;

import com.ferrocompras.ordenescompra.shared.enums.FormatoSucursal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SucursalRequest(
    @NotBlank @Size(max = 150) String nombre,
    @NotNull FormatoSucursal formato,
    // Opcional, igual que en los demas catalogos: ausente/null en creacion equivale a true; en
    // edicion, ausente/null preserva el valor actual.
    Boolean activo
) {
}
