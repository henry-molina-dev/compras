package com.ferrocompras.ordenescompra.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CerrarRequest(
    @NotNull Boolean conforme,
    @Size(max = 250) String observacion
) {
}
