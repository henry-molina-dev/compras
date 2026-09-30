package com.ferrocompras.ordenescompra.dto.mapper;

import com.ferrocompras.ordenescompra.dto.EventoProveedorResponse;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenEventoProveedor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventoProveedorMapper {

    @Mapping(target = "object", constant = "evento_proveedor")
    @Mapping(target = "ordenCompraId", source = "ordenCompra.id")
    @Mapping(target = "createdAt", source = "fechaCreacion")
    EventoProveedorResponse toResponse(OrdenEventoProveedor evento);
}
