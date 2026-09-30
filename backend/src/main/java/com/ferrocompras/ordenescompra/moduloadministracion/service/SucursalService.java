package com.ferrocompras.ordenescompra.moduloadministracion.service;

import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.SucursalRequest;
import com.ferrocompras.ordenescompra.dto.SucursalResponse;
import com.ferrocompras.ordenescompra.dto.mapper.SucursalMapper;
import com.ferrocompras.ordenescompra.exception.ConflictoException;
import com.ferrocompras.ordenescompra.exception.RecursoNoEncontradoException;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraRepository;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import com.ferrocompras.ordenescompra.shared.entity.Sucursal;
import com.ferrocompras.ordenescompra.shared.repository.SucursalRepository;
import com.ferrocompras.ordenescompra.shared.repository.UsuarioRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SucursalService {

    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrdenCompraRepository ordenCompraRepository;
    private final SucursalMapper sucursalMapper;

    public SucursalService(
        SucursalRepository sucursalRepository,
        UsuarioRepository usuarioRepository,
        OrdenCompraRepository ordenCompraRepository,
        SucursalMapper sucursalMapper
    ) {
        this.sucursalRepository = sucursalRepository;
        this.usuarioRepository = usuarioRepository;
        this.ordenCompraRepository = ordenCompraRepository;
        this.sucursalMapper = sucursalMapper;
    }

    public ListEnvelope<SucursalResponse> listar() {
        List<SucursalResponse> data = sucursalRepository.findAll().stream()
            .map(sucursalMapper::toResponse)
            .toList();
        return ListEnvelope.of(data, false);
    }

    @Transactional
    public SucursalResponse crear(SucursalRequest request, UsuarioPrincipal actor) {
        Sucursal sucursal = sucursalMapper.toEntity(request);
        sucursal.setActivo(request.activo() == null || request.activo());
        sucursal.setCreadoPor(usuarioRepository.getReferenceById(actor.id()));
        sucursal.setModificadoPor(usuarioRepository.getReferenceById(actor.id()));
        return sucursalMapper.toResponse(sucursalRepository.save(sucursal));
    }

    @Transactional
    public SucursalResponse actualizar(Integer id, SucursalRequest request, UsuarioPrincipal actor) {
        Sucursal sucursal = sucursalRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "sucursal_no_encontrada", "No existe la sucursal indicada.", "id"));

        // El formato decide que categorias se pueden pedir y si el cliente es obligatorio
        // (Venta Directa): cambiarlo con ordenes ya creadas dejaria su historial en contradiccion
        // con las reglas vigentes. Para "cambiarlo" de verdad se desactiva la sucursal y se crea otra.
        if (sucursal.getFormato() != request.formato() && ordenCompraRepository.existsBySucursalDestinoId(id)) {
            throw new ConflictoException("formato_bloqueado",
                "No se puede cambiar el formato de una sucursal que ya tiene ordenes; desactivala y crea otra.",
                "formato");
        }

        sucursal.setNombre(request.nombre());
        sucursal.setFormato(request.formato());
        if (request.activo() != null) {
            sucursal.setActivo(request.activo());
        }
        sucursal.setModificadoPor(usuarioRepository.getReferenceById(actor.id()));
        return sucursalMapper.toResponse(sucursalRepository.save(sucursal));
    }
}
