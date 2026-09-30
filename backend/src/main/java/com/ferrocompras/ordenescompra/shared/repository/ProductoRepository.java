package com.ferrocompras.ordenescompra.shared.repository;

import com.ferrocompras.ordenescompra.shared.entity.Producto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    List<Producto> findByCategoriaId(Integer categoriaId);

    Optional<Producto> findByCodigo(String codigo);

    List<Producto> findByActivoTrueOrderByCodigoAsc();
}
