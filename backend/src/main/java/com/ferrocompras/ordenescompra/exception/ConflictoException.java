package com.ferrocompras.ordenescompra.exception;

// Un recurso existe pero su estado actual impide la operacion pedida (ej. cambiar el formato de
// una sucursal que ya tiene ordenes). Responde 409 con el tipo state_conflict_error del contrato
// y un codigo propio, distinto de "transicion_invalida" (que es solo de la maquina de estados de
// las ordenes).
public class ConflictoException extends RuntimeException {

    private final String code;
    private final String param;

    public ConflictoException(String code, String message, String param) {
        super(message);
        this.code = code;
        this.param = param;
    }

    public String getCode() {
        return code;
    }

    public String getParam() {
        return param;
    }
}
