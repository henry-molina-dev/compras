package com.ferrocompras.ordenescompra.modulocompras.service.state;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import org.springframework.stereotype.Component;

@Component
public class AprobadaState implements EstadoOrdenState {

    @Override
    public EstadoOrden estado() {
        return EstadoOrden.APROBADA;
    }

    @Override
    public void anular(OrdenCompra orden, String motivo) {
        orden.setEstado(EstadoOrden.ANULADA);
        orden.setMotivoAnulacion(motivo);
    }

    @Override
    public void cerrar(OrdenCompra orden, boolean conforme, String observacion) {
        orden.setEstado(EstadoOrden.CERRADA);
        orden.setConforme(conforme);
        orden.setObservacionCierre(observacion);
    }
}
