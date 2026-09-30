package com.ferrocompras.ordenescompra.modulocompras.service.state;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EstadoOrdenStateResolver {

    private final Map<EstadoOrden, EstadoOrdenState> estados;

    public EstadoOrdenStateResolver(List<EstadoOrdenState> implementaciones) {
        this.estados = implementaciones.stream()
            .collect(Collectors.toMap(EstadoOrdenState::estado, Function.identity()));
    }

    public EstadoOrdenState resolver(EstadoOrden estado) {
        return estados.get(estado);
    }
}
