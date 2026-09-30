package com.ferrocompras.ordenescompra.shared.repository;

import com.ferrocompras.ordenescompra.shared.entity.IdempotencyKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Integer> {

    Optional<IdempotencyKey> findByClaveAndEndpoint(String clave, String endpoint);
}
