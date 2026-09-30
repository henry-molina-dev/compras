package com.ferrocompras.ordenescompra.moduloadministracion.service;

import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.ProveedorRequest;
import com.ferrocompras.ordenescompra.dto.ProveedorResponse;
import com.ferrocompras.ordenescompra.dto.mapper.ProveedorMapper;
import com.ferrocompras.ordenescompra.exception.RecursoNoEncontradoException;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import com.ferrocompras.ordenescompra.shared.entity.Proveedor;
import com.ferrocompras.ordenescompra.shared.repository.ProveedorRepository;
import com.ferrocompras.ordenescompra.shared.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administracion del catalogo de proveedores: alta, edicion, consulta y rotacion de la clave con la
 * que cada proveedor se autentica en el webhook.
 *
 * <p>Cada proveedor tambien define cuanto se puede negociar sobre el precio de catalogo de sus
 * productos (descuento y aumento maximos, en porcentaje); con 0 y 0 no admite negociar.
 */
@Service
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProveedorMapper proveedorMapper;

    public ProveedorService(
        ProveedorRepository proveedorRepository,
        UsuarioRepository usuarioRepository,
        ProveedorMapper proveedorMapper
    ) {
        this.proveedorRepository = proveedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.proveedorMapper = proveedorMapper;
    }

    /** Lista todos los proveedores, activos o no. */
    public ListEnvelope<ProveedorResponse> listar() {
        List<ProveedorResponse> data = proveedorRepository.findAll().stream()
            .map(proveedorMapper::toResponse)
            .toList();
        return ListEnvelope.of(data, false);
    }

    /**
     * Da de alta un proveedor, activo salvo que se indique lo contrario, y le genera la clave de
     * webhook. Los limites de negociacion omitidos quedan en 0, es decir, sin negociacion.
     */
    @Transactional
    public ProveedorResponse crear(ProveedorRequest request, UsuarioPrincipal actor) {
        Proveedor proveedor = proveedorMapper.toEntity(request);
        proveedor.setActivo(request.activo() == null || request.activo());
        proveedor.setDescuentoMaximoPct(request.descuentoMaximoPct() == null ? BigDecimal.ZERO : request.descuentoMaximoPct());
        proveedor.setAumentoMaximoPct(request.aumentoMaximoPct() == null ? BigDecimal.ZERO : request.aumentoMaximoPct());
        // Generada aqui porque ProveedorRequest no expone webhookApiKey (ver ProveedorResponse):
        // es la unica forma de que un proveedor nuevo llegue a tener una clave de webhook.
        proveedor.setWebhookApiKey(generarWebhookApiKey());
        proveedor.setCreadoPor(usuarioRepository.getReferenceById(actor.id()));
        proveedor.setModificadoPor(usuarioRepository.getReferenceById(actor.id()));
        return proveedorMapper.toResponse(proveedorRepository.save(proveedor));
    }

    /**
     * Edita los datos de un proveedor. Los campos opcionales omitidos (activo y limites de
     * negociacion) conservan su valor actual.
     *
     * @throws RecursoNoEncontradoException si el proveedor no existe
     */
    @Transactional
    public ProveedorResponse actualizar(Integer id, ProveedorRequest request, UsuarioPrincipal actor) {
        Proveedor proveedor = buscarOrLanzar(id);
        proveedor.setNombre(request.nombre());
        proveedor.setEmail(request.email());
        proveedor.setTelefono(request.telefono());
        proveedor.setDireccion(request.direccion());
        if (request.activo() != null) {
            proveedor.setActivo(request.activo());
        }
        if (request.descuentoMaximoPct() != null) {
            proveedor.setDescuentoMaximoPct(request.descuentoMaximoPct());
        }
        if (request.aumentoMaximoPct() != null) {
            proveedor.setAumentoMaximoPct(request.aumentoMaximoPct());
        }
        proveedor.setModificadoPor(usuarioRepository.getReferenceById(actor.id()));
        return proveedorMapper.toResponse(proveedorRepository.save(proveedor));
    }

    /**
     * Genera una clave de webhook nueva. La anterior deja de autenticar de inmediato, asi que la
     * nueva debe hacerse llegar al proveedor por un canal aparte.
     *
     * @throws RecursoNoEncontradoException si el proveedor no existe
     */
    @Transactional
    public ProveedorResponse regenerarClaveWebhook(Integer id, UsuarioPrincipal actor) {
        Proveedor proveedor = buscarOrLanzar(id);
        proveedor.setWebhookApiKey(generarWebhookApiKey());
        proveedor.setModificadoPor(usuarioRepository.getReferenceById(actor.id()));
        return proveedorMapper.toResponse(proveedorRepository.save(proveedor));
    }

    private Proveedor buscarOrLanzar(Integer id) {
        return proveedorRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "proveedor_no_encontrado", "No existe el proveedor indicado.", "id"));
    }

    private String generarWebhookApiKey() {
        return "whsk_" + UUID.randomUUID().toString().replace("-", "");
    }
}
