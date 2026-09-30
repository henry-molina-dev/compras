package com.ferrocompras.ordenescompra.exception;

// Cubre cualquier violacion de una regla de negocio sobre datos de entrada (categoria no
// permitida, cliente obligatorio/no aplicable, etc.) - responde 400 con el objeto de error
// tipado de la API, nunca un mensaje suelto.
public class NegocioException extends RuntimeException {

    private final String code;
    private final String param;

    public NegocioException(String code, String message, String param) {
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
