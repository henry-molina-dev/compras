package com.ferrocompras.ordenescompra.modulocompras.service.state;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;

// Cada implementacion representa un estado de la orden y solo sobreescribe las transiciones
// que le son validas desde ahi; el resto queda cubierto por el rechazo por defecto.
public interface EstadoOrdenState {

    EstadoOrden estado();

    default void aprobar(OrdenCompra orden) {
        throw transicionInvalida("aprobar");
    }

    default void anular(OrdenCompra orden, String motivo) {
        throw transicionInvalida("anular");
    }

    default void cerrar(OrdenCompra orden, boolean conforme, String observacion) {
        throw transicionInvalida("cerrar");
    }

    private EstadoInvalidoException transicionInvalida(String accion) {
        return new EstadoInvalidoException(
            "No se puede %s una orden en estado %s.".formatted(accion, estado()));
    }
}
