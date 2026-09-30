package com.ferrocompras.ordenescompra.dto.mapper;

import com.ferrocompras.ordenescompra.dto.ClienteRequest;
import com.ferrocompras.ordenescompra.dto.ClienteResponse;
import com.ferrocompras.ordenescompra.shared.entity.Cliente;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClienteMapper {

    @Mapping(target = "object", constant = "cliente")
    @Mapping(target = "createdAt", source = "fechaCreacion")
    @Mapping(target = "updatedAt", source = "fechaModificacion")
    ClienteResponse toResponse(Cliente cliente);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "creadoPor", ignore = true)
    @Mapping(target = "modificadoPor", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    Cliente toEntity(ClienteRequest request);
}
