package com.ferrocompras.ordenescompra.webhooks;

import com.ferrocompras.ordenescompra.dto.EventoProveedorRequest;
import com.ferrocompras.ordenescompra.dto.EventoProveedorResponse;
import com.ferrocompras.ordenescompra.dto.mapper.EventoProveedorMapper;
import com.ferrocompras.ordenescompra.exception.RecursoNoEncontradoException;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenEventoProveedor;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraRepository;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenEventoProveedorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recibe los eventos logisticos que el proveedor reporta de forma asincrona (aceptacion, despacho,
 * entrega, etc.) y los registra en la linea de tiempo de la orden.
 *
 * <p>Los eventos son puramente informativos: ninguno, ni siquiera ENTREGADA, dispara una transicion
 * de estado interna, y no se valida secuencia entre tipos de evento. Toda transicion sigue siendo
 * una accion explicita de un usuario interno.
 */
@Service
public class WebhookService {

    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenEventoProveedorRepository eventoRepository;
    private final EventoProveedorMapper eventoProveedorMapper;

    public WebhookService(
        OrdenCompraRepository ordenCompraRepository,
        OrdenEventoProveedorRepository eventoRepository,
        EventoProveedorMapper eventoProveedorMapper
    ) {
        this.ordenCompraRepository = ordenCompraRepository;
        this.eventoRepository = eventoRepository;
        this.eventoProveedorMapper = eventoProveedorMapper;
    }

    /**
     * Registra un evento reportado por el proveedor autenticado sobre una de sus ordenes, identificada
     * por su numero de orden.
     *
     * @throws RecursoNoEncontradoException si no existe una orden con ese numero para este proveedor;
     *     una orden de otro proveedor se trata igual que una inexistente, para no revelar que existe
     */
    @Transactional
    public EventoProveedorResponse registrarEvento(EventoProveedorRequest request, ProveedorPrincipal autenticado) {
        // El proveedor es el dueno de la api key; una orden de otro proveedor se trata igual que una
        // inexistente (404) para no revelar que existe.
        OrdenCompra orden = ordenCompraRepository.findByNumeroOrden(request.numeroOrden())
            .filter(o -> o.getProveedor().getId().equals(autenticado.id()))
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "orden_no_encontrada", "No existe una orden con ese numero para este proveedor.", "numero_orden"));

        OrdenEventoProveedor evento = OrdenEventoProveedor.builder()
            .ordenCompra(orden)
            .tipoEvento(request.tipoEvento())
            .observacion(request.observacion())
            .fechaEvento(request.fechaEvento())
            .build();

        return eventoProveedorMapper.toResponse(eventoRepository.save(evento));
    }
}
