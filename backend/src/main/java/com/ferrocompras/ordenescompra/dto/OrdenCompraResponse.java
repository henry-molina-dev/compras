package com.ferrocompras.ordenescompra.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OrdenCompraResponse(
    String object,
    Integer id,
    String numeroOrden,
    EstadoOrden estado,
    Integer proveedorId,
    Integer sucursalDestinoId,
    Integer clienteId,
    // Denormalizado por la misma razon que el nombre de producto en el detalle: el gerente de
    // sucursal ve la orden pero no tiene acceso al catalogo de clientes.
    String clienteNombre,
    LocalDate fechaNecesaria,
    BigDecimal total,
    String motivoAnulacion,
    Boolean conforme,
    String observacionCierre,
    List<OrdenCompraDetalleResponse> detalle,
    Instant createdAt,
    Instant updatedAt,
    // Ambos campos solo estan presentes cuando el cliente pide ?expand=proveedor /
    // ?expand=linea_tiempo respectivamente; ausentes (no null) el resto del tiempo para mantener
    // la respuesta liviana, tal como describe la convencion de expand.
    @JsonInclude(JsonInclude.Include.NON_NULL) ProveedorResponse proveedor,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<LineaTiempoItemResponse> lineaTiempo
) {

    public OrdenCompraResponse conProveedorExpandido(ProveedorResponse proveedorExpandido) {
        return new OrdenCompraResponse(object, id, numeroOrden, estado, proveedorId, sucursalDestinoId, clienteId, clienteNombre,
            fechaNecesaria, total, motivoAnulacion, conforme, observacionCierre, detalle, createdAt, updatedAt,
            proveedorExpandido, lineaTiempo);
    }

    public OrdenCompraResponse conLineaTiempoExpandida(List<LineaTiempoItemResponse> lineaTiempoExpandida) {
        return new OrdenCompraResponse(object, id, numeroOrden, estado, proveedorId, sucursalDestinoId, clienteId, clienteNombre,
            fechaNecesaria, total, motivoAnulacion, conforme, observacionCierre, detalle, createdAt, updatedAt,
            proveedor, lineaTiempoExpandida);
    }
}
