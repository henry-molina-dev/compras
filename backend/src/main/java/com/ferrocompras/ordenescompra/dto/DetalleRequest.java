package com.ferrocompras.ordenescompra.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record DetalleRequest(
    @NotNull Integer productoId,
    @NotNull @Positive BigDecimal cantidad,
    // Opcional: precio unitario negociado. Omitido = precio de catalogo (o, al editar una orden, el
    // que la linea ya tenia). Debe caer dentro de los limites del proveedor.
    @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal precioUnitario
) {
}
