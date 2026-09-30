package com.ferrocompras.ordenescompra.moduloadministracion.service;

import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.UsuarioRequest;
import com.ferrocompras.ordenescompra.dto.UsuarioResponse;
import com.ferrocompras.ordenescompra.dto.mapper.UsuarioMapper;
import com.ferrocompras.ordenescompra.exception.NegocioException;
import com.ferrocompras.ordenescompra.exception.RecursoNoEncontradoException;
import com.ferrocompras.ordenescompra.shared.entity.Sucursal;
import com.ferrocompras.ordenescompra.shared.entity.Usuario;
import com.ferrocompras.ordenescompra.shared.enums.Rol;
import com.ferrocompras.ordenescompra.shared.repository.SucursalRepository;
import com.ferrocompras.ordenescompra.shared.repository.UsuarioRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(
        UsuarioRepository usuarioRepository,
        SucursalRepository sucursalRepository,
        UsuarioMapper usuarioMapper,
        PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.sucursalRepository = sucursalRepository;
        this.usuarioMapper = usuarioMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public ListEnvelope<UsuarioResponse> listar() {
        List<UsuarioResponse> data = usuarioRepository.findAll().stream()
            .map(usuarioMapper::toResponse)
            .toList();
        return ListEnvelope.of(data, false);
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        if (request.password() == null) {
            throw new NegocioException("password_obligatoria", "password es obligatorio al crear un usuario.", "password");
        }
        Usuario usuario = usuarioMapper.toEntity(request);
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setSucursal(resolverSucursal(request.rol(), request.sucursalId()));
        usuario.setActivo(request.activo() == null || request.activo());
        return usuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse actualizar(Integer id, UsuarioRequest request) {
        Usuario usuario = buscarOrLanzar(id);
        usuario.setUsername(request.username());
        // Omitir el password en una edicion conserva el actual: permite cambiar rol, sucursal o
        // activo sin obligar a quien administra a conocer o reasignar la clave del usuario.
        if (request.password() != null) {
            usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        usuario.setRol(request.rol());
        usuario.setSucursal(resolverSucursal(request.rol(), request.sucursalId()));
        if (request.activo() != null) {
            usuario.setActivo(request.activo());
        }
        return usuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    private Sucursal resolverSucursal(Rol rol, Integer sucursalId) {
        if (rol == Rol.GERENTE_SUCURSAL) {
            if (sucursalId == null) {
                throw new NegocioException("sucursal_obligatoria",
                    "sucursal_id es obligatorio cuando el rol es GERENTE_SUCURSAL.", "sucursal_id");
            }
            return sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                    "sucursal_no_encontrada", "No existe la sucursal indicada.", "sucursal_id"));
        }
        return null;
    }

    private Usuario buscarOrLanzar(Integer id) {
        return usuarioRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "usuario_no_encontrado", "No existe el usuario indicado.", "id"));
    }
}
