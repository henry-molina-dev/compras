package com.ferrocompras.ordenescompra.dto.mapper;

import com.ferrocompras.ordenescompra.dto.SucursalRequest;
import com.ferrocompras.ordenescompra.dto.SucursalResponse;
import com.ferrocompras.ordenescompra.shared.entity.Sucursal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SucursalMapper {

    @Mapping(target = "object", constant = "sucursal")
    @Mapping(target = "createdAt", source = "fechaCreacion")
    @Mapping(target = "updatedAt", source = "fechaModificacion")
    SucursalResponse toResponse(Sucursal sucursal);

    // activo, autoria y timestamps quedan a cargo del servicio, no de este mapeo estructural.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "creadoPor", ignore = true)
    @Mapping(target = "modificadoPor", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    Sucursal toEntity(SucursalRequest request);
}
