package com.ferrocompras.ordenescompra.dto.mapper;

import com.ferrocompras.ordenescompra.dto.AuditoriaEventoResponse;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompraAuditoria;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuditoriaEventoMapper {

    @Mapping(target = "object", constant = "auditoria_evento")
    @Mapping(target = "ordenCompraId", source = "ordenCompra.id")
    @Mapping(target = "usuarioId", source = "usuario.id")
    AuditoriaEventoResponse toResponse(OrdenCompraAuditoria auditoria);
}
