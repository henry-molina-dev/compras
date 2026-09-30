package com.ferrocompras.ordenescompra.moduloadministracion.controller;

import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.ProductoRequest;
import com.ferrocompras.ordenescompra.dto.ProductoResponse;
import com.ferrocompras.ordenescompra.moduloadministracion.service.ProductoService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR')")
    public ListEnvelope<ProductoResponse> listar(
        @RequestParam(name = "categoria_id", required = false) Integer categoriaId,
        @RequestParam(name = "sucursal_id", required = false) Integer sucursalId
    ) {
        return productoService.listar(categoriaId, sucursalId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductoResponse> crear(
        @Valid @RequestBody ProductoRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        ProductoResponse creado = productoService.crear(request, actor);
        return ResponseEntity.created(URI.create("/api/productos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductoResponse actualizar(
        @PathVariable Integer id,
        @Valid @RequestBody ProductoRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return productoService.actualizar(id, request, actor);
    }
}
