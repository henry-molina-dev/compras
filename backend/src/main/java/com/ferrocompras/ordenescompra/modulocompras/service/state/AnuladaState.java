package com.ferrocompras.ordenescompra.modulocompras.service.state;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import org.springframework.stereotype.Component;

@Component
public class AnuladaState implements EstadoOrdenState {

    @Override
    public EstadoOrden estado() {
        return EstadoOrden.ANULADA;
    }
}
