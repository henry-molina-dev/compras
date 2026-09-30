package com.ferrocompras.ordenescompra.shared.repository;

import com.ferrocompras.ordenescompra.shared.entity.Cliente;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    // nombre no es unique en la base de datos: puede devolver mas de una fila, y quien llama debe
    // decidir que hacer con la ambiguedad (ver ImportacionOrdenesService).
    List<Cliente> findByNombreIgnoreCase(String nombre);

    List<Cliente> findByActivoTrueOrderByNombreAsc();
}
