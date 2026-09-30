package com.ferrocompras.ordenescompra.dto.mapper;

import com.ferrocompras.ordenescompra.dto.OrdenCompraResponse;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = OrdenCompraDetalleMapper.class)
public interface OrdenCompraMapper {

    @Mapping(target = "object", constant = "orden_compra")
    @Mapping(target = "proveedorId", source = "proveedor.id")
    @Mapping(target = "sucursalDestinoId", source = "sucursalDestino.id")
    @Mapping(target = "clienteId", source = "cliente.id")
    @Mapping(target = "clienteNombre", source = "cliente.nombre")
    @Mapping(target = "createdAt", source = "fechaCreacion")
    @Mapping(target = "updatedAt", source = "fechaModificacion")
    // El proveedor y la linea de tiempo expandidos los agrega el servicio (solo si el cliente pidio
    // ?expand=proveedor / ?expand=linea_tiempo), nunca este mapeo estructural.
    @Mapping(target = "proveedor", ignore = true)
    @Mapping(target = "lineaTiempo", ignore = true)
    OrdenCompraResponse toResponse(OrdenCompra ordenCompra);
}
