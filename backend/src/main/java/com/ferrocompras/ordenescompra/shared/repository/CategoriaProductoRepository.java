package com.ferrocompras.ordenescompra.shared.repository;

import com.ferrocompras.ordenescompra.shared.entity.CategoriaProducto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaProductoRepository extends JpaRepository<CategoriaProducto, Integer> {
}
