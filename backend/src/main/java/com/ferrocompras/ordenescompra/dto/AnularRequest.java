package com.ferrocompras.ordenescompra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnularRequest(
    @NotBlank @Size(max = 250) String motivo
) {
}
