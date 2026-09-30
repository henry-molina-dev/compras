package com.ferrocompras.ordenescompra.dto;

import java.math.BigDecimal;

public record OrdenCompraDetalleResponse(
    String object,
    Integer id,
    Integer productoId,
    // Denormalizados para que quien solo puede ver la orden (p. ej. el gerente de sucursal, sin
    // acceso al catalogo de productos) sepa que mercaderia esta recibiendo.
    String productoCodigo,
    String productoNombre,
    BigDecimal cantidad,
    BigDecimal precioUnitario,
    // Precio de catalogo al crear la linea: si difiere de precioUnitario, hubo negociacion.
    BigDecimal precioCatalogo,
    BigDecimal subtotal
) {
}
