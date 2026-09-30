package com.ferrocompras.ordenescompra.modulocompras.repository;

import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompraAuditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenCompraAuditoriaRepository extends JpaRepository<OrdenCompraAuditoria, Integer> {

    List<OrdenCompraAuditoria> findByOrdenCompraIdOrderByFechaAsc(Integer ordenCompraId);
}
