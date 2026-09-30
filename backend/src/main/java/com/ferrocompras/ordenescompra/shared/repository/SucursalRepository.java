package com.ferrocompras.ordenescompra.shared.repository;

import com.ferrocompras.ordenescompra.shared.entity.Sucursal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SucursalRepository extends JpaRepository<Sucursal, Integer> {

    // nombre no es unique en la base de datos: puede devolver mas de una fila, y quien llama debe
    // decidir que hacer con la ambiguedad (ver ImportacionOrdenesService).
    List<Sucursal> findByNombreIgnoreCase(String nombre);

    List<Sucursal> findByActivoTrueOrderByNombreAsc();
}
