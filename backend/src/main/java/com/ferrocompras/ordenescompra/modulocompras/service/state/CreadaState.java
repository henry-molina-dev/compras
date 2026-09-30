package com.ferrocompras.ordenescompra.modulocompras.service.state;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import org.springframework.stereotype.Component;

@Component
public class CreadaState implements EstadoOrdenState {

    @Override
    public EstadoOrden estado() {
        return EstadoOrden.CREADA;
    }

    @Override
    public void aprobar(OrdenCompra orden) {
        orden.setEstado(EstadoOrden.APROBADA);
    }

    @Override
    public void anular(OrdenCompra orden, String motivo) {
        orden.setEstado(EstadoOrden.ANULADA);
        orden.setMotivoAnulacion(motivo);
    }
}
