package com.ferrocompras.ordenescompra.dto;

import java.util.List;

public record ImportacionLoteResponse(
    String object,
    List<OrdenCreadaLoteResponse> ordenesCreadas,
    List<OrdenRechazadaLoteResponse> ordenesRechazadas
) {

    public static ImportacionLoteResponse of(
        List<OrdenCreadaLoteResponse> creadas, List<OrdenRechazadaLoteResponse> rechazadas
    ) {
        return new ImportacionLoteResponse("importacion_lote", creadas, rechazadas);
    }
}
