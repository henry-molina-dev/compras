package com.ferrocompras.ordenescompra.api;

import com.ferrocompras.ordenescompra.shared.entity.IdempotencyKey;
import com.ferrocompras.ordenescompra.shared.repository.IdempotencyKeyRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    // "un plazo razonable (ej. 24 horas)": una clave mas antigua que esto se trata como si no
    // existiera, y la fila vieja se reemplaza en vez de reprocesar indefinidamente prohibido.
    private static final Duration VENTANA_VIGENCIA = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> buscarVigente(String clave, String endpoint) {
        return repository.findByClaveAndEndpoint(clave, endpoint)
            .filter(existente -> existente.getFechaCreacion().isAfter(Instant.now().minus(VENTANA_VIGENCIA)));
    }

    @Transactional
    public void guardar(String clave, String endpoint, int statusCode, String responseBody) {
        // Reemplaza una entrada vencida de la misma clave+endpoint si existiera (la unica que
        // buscarVigente no habria devuelto).
        repository.findByClaveAndEndpoint(clave, endpoint).ifPresent(repository::delete);
        IdempotencyKey nueva = IdempotencyKey.builder()
            .clave(clave)
            .endpoint(endpoint)
            .statusCode(statusCode)
            .responseBody(responseBody)
            .build();
        repository.save(nueva);
    }
}
