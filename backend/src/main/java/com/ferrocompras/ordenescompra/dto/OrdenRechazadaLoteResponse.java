package com.ferrocompras.ordenescompra.dto;

public record OrdenRechazadaLoteResponse(String referenciaLote, ErrorResponse.ErrorDetail error) {
}
