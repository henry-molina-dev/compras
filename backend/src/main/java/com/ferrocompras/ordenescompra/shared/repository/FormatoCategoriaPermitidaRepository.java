package com.ferrocompras.ordenescompra.shared.repository;

import com.ferrocompras.ordenescompra.shared.entity.FormatoCategoriaPermitida;
import com.ferrocompras.ordenescompra.shared.enums.FormatoSucursal;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormatoCategoriaPermitidaRepository extends JpaRepository<FormatoCategoriaPermitida, Integer> {

    List<FormatoCategoriaPermitida> findByFormato(FormatoSucursal formato);

    @Query("SELECT f.categoria.id FROM FormatoCategoriaPermitida f WHERE f.formato = :formato")
    Set<Integer> idsDeCategoriasPermitidas(@Param("formato") FormatoSucursal formato);
}
