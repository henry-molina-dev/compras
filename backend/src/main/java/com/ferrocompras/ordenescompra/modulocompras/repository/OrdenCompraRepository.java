package com.ferrocompras.ordenescompra.modulocompras.repository;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Integer> {

    boolean existsBySucursalDestinoId(Integer sucursalId);

    Optional<OrdenCompra> findByNumeroOrden(String numeroOrden);

    // numero_orden es lexicograficamente ordenable (prefijo de anio + consecutivo de 6 digitos
    // con ceros a la izquierda), por lo que sirve directamente como cursor de paginacion. Se
    // listan de mas reciente a mas antigua, asi que el cursor avanza hacia numeros menores.
    // El filtro numeroOrden es independiente del cursor: busca coincidencia parcial (el usuario
    // rara vez recuerda el numero completo), sin distinguir mayusculas/minusculas.
    // fechaDesde/fechaHasta siempre van acotados (ver OrdenCompraService.limitesFecha): un
    // parametro LocalDate nulo dentro de "X IS NULL OR ..." hace que Hibernate/PostgreSQL no
    // puedan inferir su tipo (falla en runtime con "cannot cast type bytea to date"), asi que se
    // evita pasar null aqui en vez de intentar tipar el parametro dentro de la consulta.
    @Query("""
        SELECT o FROM OrdenCompra o
        WHERE (:estado IS NULL OR o.estado = :estado)
          AND (:proveedorId IS NULL OR o.proveedor.id = :proveedorId)
          AND (:sucursalId IS NULL OR o.sucursalDestino.id = :sucursalId)
          AND (:clienteId IS NULL OR o.cliente.id = :clienteId)
          AND (:numeroOrden IS NULL OR UPPER(o.numeroOrden) LIKE UPPER(CONCAT('%', CAST(:numeroOrden AS string), '%')))
          AND o.fechaNecesaria >= :fechaDesde
          AND o.fechaNecesaria <= :fechaHasta
          AND (:cursor IS NULL OR o.numeroOrden < :cursor)
        ORDER BY o.numeroOrden DESC
        """)
    List<OrdenCompra> buscar(
        @Param("estado") EstadoOrden estado,
        @Param("proveedorId") Integer proveedorId,
        @Param("sucursalId") Integer sucursalId,
        @Param("clienteId") Integer clienteId,
        @Param("numeroOrden") String numeroOrden,
        @Param("fechaDesde") LocalDate fechaDesde,
        @Param("fechaHasta") LocalDate fechaHasta,
        @Param("cursor") String cursor,
        Pageable pageable
    );
}
