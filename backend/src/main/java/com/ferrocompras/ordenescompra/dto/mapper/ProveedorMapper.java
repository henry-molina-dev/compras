package com.ferrocompras.ordenescompra.dto.mapper;

import com.ferrocompras.ordenescompra.dto.ProveedorRequest;
import com.ferrocompras.ordenescompra.dto.ProveedorResponse;
import com.ferrocompras.ordenescompra.shared.entity.Proveedor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProveedorMapper {

    @Mapping(target = "object", constant = "proveedor")
    @Mapping(target = "createdAt", source = "fechaCreacion")
    @Mapping(target = "updatedAt", source = "fechaModificacion")
    ProveedorResponse toResponse(Proveedor proveedor);

    // Campos no cubiertos por el request (webhookApiKey, activo, autoria, timestamps) quedan a
    // cargo del servicio, no de este mapeo puramente estructural.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "webhookApiKey", ignore = true)
    @Mapping(target = "descuentoMaximoPct", ignore = true)
    @Mapping(target = "aumentoMaximoPct", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "creadoPor", ignore = true)
    @Mapping(target = "modificadoPor", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    Proveedor toEntity(ProveedorRequest request);
}
