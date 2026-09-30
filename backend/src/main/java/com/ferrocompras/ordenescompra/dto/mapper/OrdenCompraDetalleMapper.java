package com.ferrocompras.ordenescompra.dto.mapper;

import com.ferrocompras.ordenescompra.dto.OrdenCompraDetalleResponse;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompraDetalle;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrdenCompraDetalleMapper {

    @Mapping(target = "object", constant = "orden_compra_detalle")
    @Mapping(target = "productoId", source = "producto.id")
    @Mapping(target = "productoCodigo", source = "producto.codigo")
    @Mapping(target = "productoNombre", source = "producto.nombre")
    OrdenCompraDetalleResponse toResponse(OrdenCompraDetalle detalle);

    List<OrdenCompraDetalleResponse> toResponseList(List<OrdenCompraDetalle> detalle);
}
