package com.ferrocompras.ordenescompra;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferrocompras.ordenescompra.shared.entity.Producto;
import com.ferrocompras.ordenescompra.shared.enums.FormatoSucursal;
import com.ferrocompras.ordenescompra.shared.repository.ProductoRepository;
import com.ferrocompras.ordenescompra.shared.repository.SucursalRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Al arrancar el contexto con hibernate.ddl-auto=validate, Hibernate compara cada entidad contra
// el esquema real aplicado por Flyway y falla si alguna columna, tipo o relacion no coincide.
@Testcontainers
@SpringBootTest
class OrdenesCompraApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void lasSucursalesSemillaCubrenLosTresFormatos() {
        List<FormatoSucursal> formatos = sucursalRepository.findAll().stream()
            .map(s -> s.getFormato())
            .toList();

        assertThat(formatos).containsExactlyInAnyOrder(
            FormatoSucursal.FERRETERIA, FormatoSucursal.FERRETERIA_CONSTRUCCION, FormatoSucursal.VENTA_DIRECTA);
    }

    @Test
    @Transactional
    void unProductoSemillaResuelveSuCategoriaPorRelacion() {
        Optional<Producto> tornillo = productoRepository.findAll().stream()
            .filter(p -> p.getCodigo().equals("TORN-05"))
            .findFirst();

        assertThat(tornillo).isPresent();
        assertThat(tornillo.get().getCategoria().getNombre()).isEqualTo("HERRAMIENTAS");
    }
}
