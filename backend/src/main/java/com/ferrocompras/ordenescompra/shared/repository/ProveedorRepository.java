package com.ferrocompras.ordenescompra.shared.repository;

import com.ferrocompras.ordenescompra.shared.entity.Proveedor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {

    Optional<Proveedor> findByWebhookApiKey(String webhookApiKey);

    // nombre no es unique en la base de datos: puede devolver mas de una fila, y quien llama debe
    // decidir que hacer con la ambiguedad (ver ImportacionOrdenesService).
    List<Proveedor> findByNombreIgnoreCase(String nombre);

    List<Proveedor> findByActivoTrueOrderByNombreAsc();
}
