package com.ferrocompras.ordenescompra.moduloadministracion.controller;

import com.ferrocompras.ordenescompra.dto.CategoriaResponse;
import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.moduloadministracion.service.CategoriaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Sin @PreAuthorize, igual que sucursales: es un catalogo de referencia sin datos sensibles que
// necesitan las pantallas de alta y edicion de productos.
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public ListEnvelope<CategoriaResponse> listar() {
        return categoriaService.listar();
    }
}
