package com.ferrocompras.ordenescompra.modulocompras.repository;

import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenEventoProveedor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenEventoProveedorRepository extends JpaRepository<OrdenEventoProveedor, Integer> {

    List<OrdenEventoProveedor> findByOrdenCompraIdOrderByFechaEventoAsc(Integer ordenCompraId);
}
