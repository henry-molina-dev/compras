package com.ferrocompras.ordenescompra.dto;

import com.ferrocompras.ordenescompra.modulocompras.entity.TipoEventoProveedor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EventoProveedorRequest(
    @NotBlank @Size(max = 20) String numeroOrden,
    @NotNull TipoEventoProveedor tipoEvento,
    @Size(max = 250) String observacion,
    @NotNull Instant fechaEvento
) {
}
