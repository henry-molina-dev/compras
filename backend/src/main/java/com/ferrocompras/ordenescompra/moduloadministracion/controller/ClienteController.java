package com.ferrocompras.ordenescompra.moduloadministracion.controller;

import com.ferrocompras.ordenescompra.dto.ClienteRequest;
import com.ferrocompras.ordenescompra.dto.ClienteResponse;
import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.moduloadministracion.service.ClienteService;
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
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR')")
    public ListEnvelope<ClienteResponse> listar() {
        return clienteService.listar();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClienteResponse> crear(
        @Valid @RequestBody ClienteRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        ClienteResponse creado = clienteService.crear(request, actor);
        return ResponseEntity.created(URI.create("/api/clientes/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClienteResponse actualizar(
        @PathVariable Integer id,
        @Valid @RequestBody ClienteRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return clienteService.actualizar(id, request, actor);
    }
}
