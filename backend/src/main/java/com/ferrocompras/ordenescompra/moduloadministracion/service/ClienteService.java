package com.ferrocompras.ordenescompra.moduloadministracion.service;

import com.ferrocompras.ordenescompra.dto.ClienteRequest;
import com.ferrocompras.ordenescompra.dto.ClienteResponse;
import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.mapper.ClienteMapper;
import com.ferrocompras.ordenescompra.exception.RecursoNoEncontradoException;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import com.ferrocompras.ordenescompra.shared.entity.Cliente;
import com.ferrocompras.ordenescompra.shared.repository.ClienteRepository;
import com.ferrocompras.ordenescompra.shared.repository.UsuarioRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteMapper clienteMapper;

    public ClienteService(
        ClienteRepository clienteRepository,
        UsuarioRepository usuarioRepository,
        ClienteMapper clienteMapper
    ) {
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.clienteMapper = clienteMapper;
    }

    public ListEnvelope<ClienteResponse> listar() {
        List<ClienteResponse> data = clienteRepository.findAll().stream()
            .map(clienteMapper::toResponse)
            .toList();
        return ListEnvelope.of(data, false);
    }

    @Transactional
    public ClienteResponse crear(ClienteRequest request, UsuarioPrincipal actor) {
        Cliente cliente = clienteMapper.toEntity(request);
        cliente.setActivo(request.activo() == null || request.activo());
        cliente.setCreadoPor(usuarioRepository.getReferenceById(actor.id()));
        cliente.setModificadoPor(usuarioRepository.getReferenceById(actor.id()));
        return clienteMapper.toResponse(clienteRepository.save(cliente));
    }

    @Transactional
    public ClienteResponse actualizar(Integer id, ClienteRequest request, UsuarioPrincipal actor) {
        Cliente cliente = buscarOrLanzar(id);
        cliente.setNombre(request.nombre());
        cliente.setTipo(request.tipo());
        cliente.setEmail(request.email());
        if (request.activo() != null) {
            cliente.setActivo(request.activo());
        }
        cliente.setModificadoPor(usuarioRepository.getReferenceById(actor.id()));
        return clienteMapper.toResponse(clienteRepository.save(cliente));
    }

    private Cliente buscarOrLanzar(Integer id) {
        return clienteRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "cliente_no_encontrado", "No existe el cliente indicado.", "id"));
    }
}
