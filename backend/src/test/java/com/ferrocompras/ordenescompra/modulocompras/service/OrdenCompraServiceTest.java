package com.ferrocompras.ordenescompra.modulocompras.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferrocompras.ordenescompra.dto.DetalleRequest;
import com.ferrocompras.ordenescompra.dto.OrdenCompraRequest;
import com.ferrocompras.ordenescompra.dto.mapper.OrdenCompraMapper;
import com.ferrocompras.ordenescompra.dto.mapper.ProveedorMapper;
import com.ferrocompras.ordenescompra.exception.NegocioException;
import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompraDetalle;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraAuditoriaRepository;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraRepository;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenEventoProveedorRepository;
import com.ferrocompras.ordenescompra.modulocompras.service.state.AprobadaState;
import com.ferrocompras.ordenescompra.modulocompras.service.state.CreadaState;
import com.ferrocompras.ordenescompra.modulocompras.service.state.EstadoOrdenStateResolver;
import com.ferrocompras.ordenescompra.notificaciones.OrdenAnuladaEvent;
import com.ferrocompras.ordenescompra.notificaciones.OrdenAprobadaEvent;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import com.ferrocompras.ordenescompra.shared.entity.CategoriaProducto;
import com.ferrocompras.ordenescompra.shared.entity.Producto;
import com.ferrocompras.ordenescompra.shared.entity.Proveedor;
import com.ferrocompras.ordenescompra.shared.entity.Sucursal;
import com.ferrocompras.ordenescompra.shared.entity.Usuario;
import com.ferrocompras.ordenescompra.shared.enums.FormatoSucursal;
import com.ferrocompras.ordenescompra.shared.enums.Rol;
import com.ferrocompras.ordenescompra.shared.repository.ClienteRepository;
import com.ferrocompras.ordenescompra.shared.repository.FormatoCategoriaPermitidaRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProductoRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProveedorRepository;
import com.ferrocompras.ordenescompra.shared.repository.SucursalRepository;
import com.ferrocompras.ordenescompra.shared.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrdenCompraServiceTest {

    @Mock private OrdenCompraRepository ordenCompraRepository;
    @Mock private OrdenCompraAuditoriaRepository auditoriaRepository;
    @Mock private OrdenEventoProveedorRepository eventoProveedorRepository;
    @Mock private ProveedorRepository proveedorRepository;
    @Mock private SucursalRepository sucursalRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private FormatoCategoriaPermitidaRepository formatoCategoriaPermitidaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NumeroOrdenGenerator numeroOrdenGenerator;
    @Mock private EstadoOrdenStateResolver estadoOrdenStateResolver;
    @Mock private OrdenCompraMapper ordenCompraMapper;
    @Mock private ProveedorMapper proveedorMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OrdenCompraPdfRenderer pdfRenderer;

    private OrdenCompraService service;

    @BeforeEach
    void setUp() {
        service = new OrdenCompraService(
            ordenCompraRepository, auditoriaRepository, eventoProveedorRepository, proveedorRepository,
            sucursalRepository, clienteRepository, productoRepository, formatoCategoriaPermitidaRepository,
            usuarioRepository, numeroOrdenGenerator, estadoOrdenStateResolver, ordenCompraMapper, proveedorMapper,
            eventPublisher, pdfRenderer);
    }

    private UsuarioPrincipal comprador() {
        return new UsuarioPrincipal(1, "maria.gomez", Rol.COMPRADOR, null);
    }

    @Test
    void calculaElTotalComoLaSumaDeLosSubtotalesDeLasLineas() {
        Sucursal sucursal = Sucursal.builder().id(2).formato(FormatoSucursal.FERRETERIA).build();
        Proveedor proveedor = Proveedor.builder().id(4).build();
        CategoriaProducto herramientas = CategoriaProducto.builder().id(1).nombre("HERRAMIENTAS").build();
        Producto tornillo = Producto.builder().id(10).nombre("Tornillo").categoria(herramientas)
            .precio(new BigDecimal("3.20")).build();
        Producto brocas = Producto.builder().id(11).nombre("Brocas").categoria(herramientas)
            .precio(new BigDecimal("18.50")).build();

        when(proveedorRepository.findById(4)).thenReturn(Optional.of(proveedor));
        when(sucursalRepository.findById(2)).thenReturn(Optional.of(sucursal));
        when(formatoCategoriaPermitidaRepository.idsDeCategoriasPermitidas(FormatoSucursal.FERRETERIA))
            .thenReturn(Set.of(1));
        when(productoRepository.findById(10)).thenReturn(Optional.of(tornillo));
        when(productoRepository.findById(11)).thenReturn(Optional.of(brocas));
        when(usuarioRepository.getReferenceById(1)).thenReturn(Usuario.builder().id(1).build());
        when(numeroOrdenGenerator.generar()).thenReturn("OC-2026-000001");
        when(ordenCompraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrdenCompraRequest request = new OrdenCompraRequest(4, 2, null, LocalDate.now().plusDays(5),
            List.of(new DetalleRequest(10, new BigDecimal("200"), null), new DetalleRequest(11, new BigDecimal("15"), null)));

        service.crearOrden(request, comprador());

        ArgumentCaptor<OrdenCompra> captor = ArgumentCaptor.forClass(OrdenCompra.class);
        verify(ordenCompraRepository).save(captor.capture());
        assertThat(captor.getValue().getTotal()).isEqualByComparingTo("917.50");
    }

    @Test
    void rechazaUnProductoDeCategoriaNoPermitidaParaElFormatoDeLaSucursal() {
        Sucursal sucursal = Sucursal.builder().id(2).formato(FormatoSucursal.FERRETERIA_CONSTRUCCION).build();
        Proveedor proveedor = Proveedor.builder().id(4).build();
        CategoriaProducto herramientas = CategoriaProducto.builder().id(1).nombre("HERRAMIENTAS").build();
        Producto tornillo = Producto.builder().id(10).nombre("Tornillo").categoria(herramientas)
            .precio(new BigDecimal("3.20")).build();

        when(proveedorRepository.findById(4)).thenReturn(Optional.of(proveedor));
        when(sucursalRepository.findById(2)).thenReturn(Optional.of(sucursal));
        // HERRAMIENTAS (id 1) no esta en el set de categorias permitidas para este formato.
        when(formatoCategoriaPermitidaRepository.idsDeCategoriasPermitidas(FormatoSucursal.FERRETERIA_CONSTRUCCION))
            .thenReturn(Set.of(99));
        when(productoRepository.findById(10)).thenReturn(Optional.of(tornillo));

        OrdenCompraRequest request = new OrdenCompraRequest(4, 2, null, LocalDate.now().plusDays(5),
            List.of(new DetalleRequest(10, new BigDecimal("200"), null)));

        NegocioException ex = catchThrowableOfType(NegocioException.class, () -> service.crearOrden(request, comprador()));

        assertThat(ex.getCode()).isEqualTo("categoria_no_permitida");
        assertThat(ex.getParam()).isEqualTo("detalle[0].producto_id");
    }

    @Test
    void exigeClienteCuandoLaSucursalDestinoEsVentaDirecta() {
        Sucursal sucursal = Sucursal.builder().id(3).formato(FormatoSucursal.VENTA_DIRECTA).build();
        Proveedor proveedor = Proveedor.builder().id(4).build();

        when(proveedorRepository.findById(4)).thenReturn(Optional.of(proveedor));
        when(sucursalRepository.findById(3)).thenReturn(Optional.of(sucursal));

        OrdenCompraRequest request = new OrdenCompraRequest(4, 3, null, LocalDate.now().plusDays(5),
            List.of(new DetalleRequest(10, BigDecimal.ONE, null)));

        NegocioException ex = catchThrowableOfType(NegocioException.class, () -> service.crearOrden(request, comprador()));

        assertThat(ex.getCode()).isEqualTo("cliente_obligatorio");
        assertThat(ex.getParam()).isEqualTo("cliente_id");
    }

    private OrdenCompra ordenDeEjemploEnCreada() {
        Proveedor proveedor = Proveedor.builder().id(4).nombre("Ferretera del Norte S.A.")
            .email("contacto@ferreteradelnorte.example.com").build();
        CategoriaProducto herramientas = CategoriaProducto.builder().id(1).nombre("HERRAMIENTAS").build();
        Producto tornillo = Producto.builder().id(10).nombre("Tornillo").categoria(herramientas)
            .precio(new BigDecimal("3.20")).unidadCompra("CAJA").build();

        OrdenCompra orden = OrdenCompra.builder()
            .id(99)
            .numeroOrden("OC-2026-000099")
            .estado(EstadoOrden.CREADA)
            .proveedor(proveedor)
            .fechaNecesaria(LocalDate.now().plusDays(5))
            .total(new BigDecimal("32.00"))
            .build();
        orden.agregarDetalle(OrdenCompraDetalle.builder()
            .producto(tornillo).cantidad(new BigDecimal("10"))
            .precioUnitario(new BigDecimal("3.20")).subtotal(new BigDecimal("32.00")).build());
        return orden;
    }

    @Test
    void aprobarPublicaElEventoDeDominioConLosDatosDeLaOrden() {
        OrdenCompra orden = ordenDeEjemploEnCreada();
        byte[] pdf = "pdf-de-prueba".getBytes();
        when(ordenCompraRepository.findById(99)).thenReturn(Optional.of(orden));
        when(estadoOrdenStateResolver.resolver(EstadoOrden.CREADA)).thenReturn(new CreadaState());
        when(pdfRenderer.renderizar(orden)).thenReturn(pdf);

        service.aprobar(99, comprador());

        ArgumentCaptor<OrdenAprobadaEvent> captor = ArgumentCaptor.forClass(OrdenAprobadaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        OrdenAprobadaEvent evento = captor.getValue();
        assertThat(evento.numeroOrden()).isEqualTo("OC-2026-000099");
        assertThat(evento.proveedorEmail()).isEqualTo("contacto@ferreteradelnorte.example.com");
        assertThat(evento.total()).isEqualByComparingTo("32.00");
        assertThat(evento.detalle()).hasSize(1);
        assertThat(evento.detalle().get(0).productoNombre()).isEqualTo("Tornillo");
        assertThat(evento.pdfOrden()).isEqualTo(pdf);
    }

    @Test
    void aprobarPublicaElEventoSinPdfSiElRenderizadoFalla() {
        // El PDF adjunto es "mejor esfuerzo": si el renderizado falla, la aprobacion (y el correo,
        // solo que sin adjunto) deben seguir su curso, no fallar por completo.
        OrdenCompra orden = ordenDeEjemploEnCreada();
        when(ordenCompraRepository.findById(99)).thenReturn(Optional.of(orden));
        when(estadoOrdenStateResolver.resolver(EstadoOrden.CREADA)).thenReturn(new CreadaState());
        when(pdfRenderer.renderizar(orden)).thenThrow(new IllegalStateException("fuente no disponible"));

        service.aprobar(99, comprador());

        ArgumentCaptor<OrdenAprobadaEvent> captor = ArgumentCaptor.forClass(OrdenAprobadaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().pdfOrden()).isNull();
    }

    @Test
    void anularUnaOrdenAprobadaPublicaElEventoDeDominioConMotivoYProveedor() {
        OrdenCompra orden = ordenDeEjemploEnCreada();
        orden.setEstado(EstadoOrden.APROBADA);
        byte[] pdf = "pdf-de-prueba".getBytes();
        when(ordenCompraRepository.findById(99)).thenReturn(Optional.of(orden));
        when(estadoOrdenStateResolver.resolver(EstadoOrden.APROBADA)).thenReturn(new AprobadaState());
        when(pdfRenderer.renderizar(orden)).thenReturn(pdf);

        service.anular(99, "Proveedor sin stock", comprador());

        ArgumentCaptor<OrdenAnuladaEvent> captor = ArgumentCaptor.forClass(OrdenAnuladaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        OrdenAnuladaEvent evento = captor.getValue();
        assertThat(evento.numeroOrden()).isEqualTo("OC-2026-000099");
        assertThat(evento.proveedorEmail()).isEqualTo("contacto@ferreteradelnorte.example.com");
        assertThat(evento.motivoAnulacion()).isEqualTo("Proveedor sin stock");
        assertThat(evento.pdfOrden()).isEqualTo(pdf);
    }

    @Test
    void anularUnaOrdenCreadaNoNotificaAlProveedorPorqueNuncaSeLeAprobo() {
        // El proveedor solo se entera de una orden cuando pasa a APROBADA; si se anula antes de
        // eso, no hay nada que cancelarle - no debe dispararse el correo de anulacion.
        OrdenCompra orden = ordenDeEjemploEnCreada();
        when(ordenCompraRepository.findById(99)).thenReturn(Optional.of(orden));
        when(estadoOrdenStateResolver.resolver(EstadoOrden.CREADA)).thenReturn(new CreadaState());

        service.anular(99, "Proveedor sin stock", comprador());

        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(OrdenAnuladaEvent.class));
    }
}
