package com.ferrocompras.ordenescompra.dto.mapper;

import com.ferrocompras.ordenescompra.dto.UsuarioRequest;
import com.ferrocompras.ordenescompra.dto.UsuarioResponse;
import com.ferrocompras.ordenescompra.shared.entity.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "object", constant = "usuario")
    @Mapping(target = "sucursalId", source = "sucursal.id")
    @Mapping(target = "createdAt", source = "fechaCreacion")
    @Mapping(target = "updatedAt", source = "fechaModificacion")
    UsuarioResponse toResponse(Usuario usuario);

    // El password llega en texto plano (UsuarioRequest.password) y nunca se mapea directo a
    // passwordHash: el servicio lo cifra explicitamente antes de asignarlo.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "sucursal", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    Usuario toEntity(UsuarioRequest request);
}
