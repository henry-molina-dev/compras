package com.ferrocompras.ordenescompra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductoRequest(
    @NotBlank @Size(max = 30) String codigo,
    @NotBlank @Size(max = 150) String nombre,
    @NotNull Integer categoriaId,
    @NotNull @Positive BigDecimal precio,
    @NotBlank String unidadVenta,
    @NotBlank String unidadCompra,
    @Positive BigDecimal factorConversion,
    // Opcional: mismo criterio que ProveedorRequest.activo.
    Boolean activo
) {
}
