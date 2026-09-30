package com.ferrocompras.ordenescompra.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProveedorRequest(
    @NotBlank @Size(max = 150) String nombre,
    @NotBlank @Email @Size(max = 150) String email,
    @Size(max = 30) String telefono,
    @Size(max = 250) String direccion,
    // Opcional: ausente/null en creacion equivale a true; en edicion, ausente/null preserva el
    // valor actual. Permite que PUT sirva de accion activar/desactivar desde la
    // administracion de catalogos.
    Boolean activo,
    // Opcionales, igual que activo: omitidos en creacion equivalen a 0 (sin negociacion); en edicion
    // preservan el valor actual. El descuento es menor a 100 para que el precio nunca llegue a cero.
    @DecimalMin("0.00") @DecimalMax("99.99") @Digits(integer = 2, fraction = 2) BigDecimal descuentoMaximoPct,
    @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal aumentoMaximoPct
) {
}
