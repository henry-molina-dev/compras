package com.ferrocompras.ordenescompra.dto.mapper;

import com.ferrocompras.ordenescompra.dto.ProductoRequest;
import com.ferrocompras.ordenescompra.dto.ProductoResponse;
import com.ferrocompras.ordenescompra.shared.entity.Producto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    @Mapping(target = "object", constant = "producto")
    @Mapping(target = "categoriaId", source = "categoria.id")
    @Mapping(target = "createdAt", source = "fechaCreacion")
    @Mapping(target = "updatedAt", source = "fechaModificacion")
    ProductoResponse toResponse(Producto producto);

    // La relacion categoria se resuelve en el servicio (requiere buscarla por categoriaId).
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "creadoPor", ignore = true)
    @Mapping(target = "modificadoPor", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    Producto toEntity(ProductoRequest request);
}
