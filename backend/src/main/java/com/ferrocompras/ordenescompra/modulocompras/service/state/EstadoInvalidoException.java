package com.ferrocompras.ordenescompra.modulocompras.service.state;

// Una orden CERRADA o ANULADA es estado final; intentar aprobar/anular/cerrar fuera de las
// transiciones permitidas responde 409, no un error de validacion generico.
public class EstadoInvalidoException extends RuntimeException {

    public EstadoInvalidoException(String message) {
        super(message);
    }
}
