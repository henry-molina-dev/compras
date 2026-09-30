package com.ferrocompras.ordenescompra.dto.mapper;

import com.ferrocompras.ordenescompra.dto.CategoriaResponse;
import com.ferrocompras.ordenescompra.shared.entity.CategoriaProducto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoriaMapper {

    @Mapping(target = "object", constant = "categoria")
    CategoriaResponse toResponse(CategoriaProducto categoria);
}
