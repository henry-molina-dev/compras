package com.ferrocompras.ordenescompra.moduloadministracion.controller;

import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.ProveedorRequest;
import com.ferrocompras.ordenescompra.dto.ProveedorResponse;
import com.ferrocompras.ordenescompra.moduloadministracion.service.ProveedorService;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import com.ferrocompras.ordenescompra.shared.enums.Rol;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    // COMPRADOR necesita el listado para elegir proveedor al crear una orden, pero la clave del
    // webhook solo la administra ADMIN: quien puede leerla puede falsificar eventos del proveedor.
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR')")
    public ListEnvelope<ProveedorResponse> listar(@AuthenticationPrincipal UsuarioPrincipal actor) {
        ListEnvelope<ProveedorResponse> listado = proveedorService.listar();
        if (actor.rol() == Rol.ADMIN) {
            return listado;
        }
        return ListEnvelope.of(listado.data().stream().map(ProveedorController::sinClaveWebhook).toList(), listado.hasMore());
    }

    private static ProveedorResponse sinClaveWebhook(ProveedorResponse p) {
        return new ProveedorResponse(p.object(), p.id(), p.nombre(), p.email(), p.telefono(), p.direccion(),
            null, p.descuentoMaximoPct(), p.aumentoMaximoPct(), p.activo(), p.createdAt(), p.updatedAt());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProveedorResponse> crear(
        @Valid @RequestBody ProveedorRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        ProveedorResponse creado = proveedorService.crear(request, actor);
        return ResponseEntity.created(URI.create("/api/proveedores/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProveedorResponse actualizar(
        @PathVariable Integer id,
        @Valid @RequestBody ProveedorRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return proveedorService.actualizar(id, request, actor);
    }

    @PatchMapping("/{id}/regenerar-clave-webhook")
    @PreAuthorize("hasRole('ADMIN')")
    public ProveedorResponse regenerarClaveWebhook(
        @PathVariable Integer id,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return proveedorService.regenerarClaveWebhook(id, actor);
    }
}
