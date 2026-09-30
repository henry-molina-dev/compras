package com.ferrocompras.ordenescompra.moduloadministracion.service;

import com.ferrocompras.ordenescompra.dto.AuditoriaEventoResponse;
import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.mapper.AuditoriaEventoMapper;
import com.ferrocompras.ordenescompra.exception.RecursoNoEncontradoException;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraAuditoriaRepository;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraAuditoriaRepository auditoriaRepository;
    private final AuditoriaEventoMapper auditoriaEventoMapper;

    public AuditoriaService(
        OrdenCompraRepository ordenCompraRepository,
        OrdenCompraAuditoriaRepository auditoriaRepository,
        AuditoriaEventoMapper auditoriaEventoMapper
    ) {
        this.ordenCompraRepository = ordenCompraRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.auditoriaEventoMapper = auditoriaEventoMapper;
    }

    // Se busca por numero_orden (ej. "OC-2026-000123"), no por el id interno de base de datos: es
    // el unico identificador de la orden que un ADMIN conoce de memoria, ya que es el que se
    // muestra en toda la aplicacion (listados, PDF, correos) - el id nunca se expone en la UI.
    public ListEnvelope<AuditoriaEventoResponse> obtenerPorOrden(String numeroOrden) {
        OrdenCompra orden = ordenCompraRepository.findByNumeroOrden(numeroOrden)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "orden_no_encontrada", "No existe la orden de compra indicada.", "numero_orden"));
        List<AuditoriaEventoResponse> data = auditoriaRepository.findByOrdenCompraIdOrderByFechaAsc(orden.getId()).stream()
            .map(auditoriaEventoMapper::toResponse)
            .toList();
        return ListEnvelope.of(data, false);
    }
}
