package com.ferrocompras.ordenescompra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record OrdenCompraRequest(
    @NotNull Integer proveedorId,
    @NotNull Integer sucursalDestinoId,
    Integer clienteId,
    @NotNull @Future LocalDate fechaNecesaria,
    @NotEmpty List<@Valid DetalleRequest> detalle
) {
}
