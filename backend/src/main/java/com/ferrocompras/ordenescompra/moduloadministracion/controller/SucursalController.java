package com.ferrocompras.ordenescompra.moduloadministracion.controller;

import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.SucursalRequest;
import com.ferrocompras.ordenescompra.dto.SucursalResponse;
import com.ferrocompras.ordenescompra.moduloadministracion.service.SucursalService;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sucursales")
public class SucursalController {

    private final SucursalService sucursalService;

    public SucursalController(SucursalService sucursalService) {
        this.sucursalService = sucursalService;
    }

    // Sin @PreAuthorize: el contrato no restringe la lectura por rol (bearerAuth global, cualquier
    // usuario autenticado) - la usan tanto pantallas de Compras (elegir sucursal destino) como de
    // Administracion (elegir sucursal de un GERENTE_SUCURSAL). Solo el alta y la edicion son de ADMIN.
    @GetMapping
    public ListEnvelope<SucursalResponse> listar() {
        return sucursalService.listar();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SucursalResponse> crear(
        @Valid @RequestBody SucursalRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        SucursalResponse creada = sucursalService.crear(request, actor);
        return ResponseEntity.created(URI.create("/api/sucursales/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SucursalResponse actualizar(
        @PathVariable Integer id,
        @Valid @RequestBody SucursalRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return sucursalService.actualizar(id, request, actor);
    }
}
