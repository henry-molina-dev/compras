package com.ferrocompras.ordenescompra.dto;

public record ErrorResponse(ErrorDetail error) {

    public record ErrorDetail(String type, String code, String message, String param) {
    }

    public static ErrorResponse of(String type, String code, String message, String param) {
        return new ErrorResponse(new ErrorDetail(type, code, message, param));
    }
}
