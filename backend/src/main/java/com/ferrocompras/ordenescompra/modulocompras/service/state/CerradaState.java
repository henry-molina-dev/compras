package com.ferrocompras.ordenescompra.modulocompras.service.state;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import org.springframework.stereotype.Component;

// La mercaderia ya ingreso a la sucursal en este estado; anularla dejaria el inventario en un
// estado inconsistente con la orden, asi que ninguna transicion es valida desde aqui.
@Component
public class CerradaState implements EstadoOrdenState {

    @Override
    public EstadoOrden estado() {
        return EstadoOrden.CERRADA;
    }
}
