package com.ferrocompras.ordenescompra.moduloadministracion.controller;

import com.ferrocompras.ordenescompra.dto.AuditoriaEventoResponse;
import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.moduloadministracion.service.AuditoriaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasRole('ADMIN')")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/ordenes/{numeroOrden}")
    public ListEnvelope<AuditoriaEventoResponse> porOrden(@PathVariable String numeroOrden) {
        return auditoriaService.obtenerPorOrden(numeroOrden);
    }
}
