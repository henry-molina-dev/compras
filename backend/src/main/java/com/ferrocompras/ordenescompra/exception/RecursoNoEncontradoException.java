package com.ferrocompras.ordenescompra.exception;

public class RecursoNoEncontradoException extends RuntimeException {

    private final String code;
    private final String param;

    public RecursoNoEncontradoException(String code, String message, String param) {
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
