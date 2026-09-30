package com.ferrocompras.ordenescompra.modulocompras.service;

import com.ferrocompras.ordenescompra.dto.DetalleRequest;
import com.ferrocompras.ordenescompra.dto.LineaTiempoItemResponse;
import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.OrdenCompraRequest;
import com.ferrocompras.ordenescompra.dto.OrdenCompraResponse;
import com.ferrocompras.ordenescompra.dto.mapper.OrdenCompraMapper;
import com.ferrocompras.ordenescompra.dto.mapper.ProveedorMapper;
import com.ferrocompras.ordenescompra.exception.NegocioException;
import com.ferrocompras.ordenescompra.exception.RecursoNoEncontradoException;
import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompraAuditoria;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompraDetalle;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenEventoProveedor;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraAuditoriaRepository;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenCompraRepository;
import com.ferrocompras.ordenescompra.modulocompras.repository.OrdenEventoProveedorRepository;
import com.ferrocompras.ordenescompra.modulocompras.service.state.EstadoInvalidoException;
import com.ferrocompras.ordenescompra.modulocompras.service.state.EstadoOrdenStateResolver;
import com.ferrocompras.ordenescompra.notificaciones.OrdenAnuladaEvent;
import com.ferrocompras.ordenescompra.notificaciones.OrdenAprobadaEvent;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import com.ferrocompras.ordenescompra.shared.entity.Cliente;
import com.ferrocompras.ordenescompra.shared.entity.Producto;
import com.ferrocompras.ordenescompra.shared.entity.Proveedor;
import com.ferrocompras.ordenescompra.shared.entity.Sucursal;
import com.ferrocompras.ordenescompra.shared.enums.FormatoSucursal;
import com.ferrocompras.ordenescompra.shared.enums.Rol;
import com.ferrocompras.ordenescompra.shared.repository.ClienteRepository;
import com.ferrocompras.ordenescompra.shared.repository.FormatoCategoriaPermitidaRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProductoRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProveedorRepository;
import com.ferrocompras.ordenescompra.shared.repository.SucursalRepository;
import com.ferrocompras.ordenescompra.shared.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de las ordenes de compra: crear, editar el detalle, consultar, listar y llevar la
 * orden por su ciclo de vida (aprobar, anular, cerrar).
 *
 * <p>Las transiciones de estado no se deciden aqui: se delegan en el estado actual de la orden, de
 * modo que cada estado solo admite las transiciones que le corresponden. Este servicio agrega lo que
 * rodea a una transicion: auditoria, notificaciones y reglas de acceso.
 *
 * <p>El acceso por sucursal es de nivel de fila: un GERENTE_SUCURSAL solo ve y cierra ordenes de su
 * propia sucursal, y la violacion se informa con {@link AccessDeniedException} (responde 403).
 */
@Service
public class OrdenCompraService {

    private static final Logger log = LoggerFactory.getLogger(OrdenCompraService.class);

    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraAuditoriaRepository auditoriaRepository;
    private final OrdenEventoProveedorRepository eventoProveedorRepository;
    private final ProveedorRepository proveedorRepository;
    private final SucursalRepository sucursalRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final FormatoCategoriaPermitidaRepository formatoCategoriaPermitidaRepository;
    private final UsuarioRepository usuarioRepository;
    private final NumeroOrdenGenerator numeroOrdenGenerator;
    private final EstadoOrdenStateResolver estadoOrdenStateResolver;
    private final OrdenCompraMapper ordenCompraMapper;
    private final ProveedorMapper proveedorMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final OrdenCompraPdfRenderer pdfRenderer;

    public OrdenCompraService(
        OrdenCompraRepository ordenCompraRepository,
        OrdenCompraAuditoriaRepository auditoriaRepository,
        OrdenEventoProveedorRepository eventoProveedorRepository,
        ProveedorRepository proveedorRepository,
        SucursalRepository sucursalRepository,
        ClienteRepository clienteRepository,
        ProductoRepository productoRepository,
        FormatoCategoriaPermitidaRepository formatoCategoriaPermitidaRepository,
        UsuarioRepository usuarioRepository,
        NumeroOrdenGenerator numeroOrdenGenerator,
        EstadoOrdenStateResolver estadoOrdenStateResolver,
        OrdenCompraMapper ordenCompraMapper,
        ProveedorMapper proveedorMapper,
        ApplicationEventPublisher eventPublisher,
        OrdenCompraPdfRenderer pdfRenderer
    ) {
        this.ordenCompraRepository = ordenCompraRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.eventoProveedorRepository = eventoProveedorRepository;
        this.proveedorRepository = proveedorRepository;
        this.sucursalRepository = sucursalRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.formatoCategoriaPermitidaRepository = formatoCategoriaPermitidaRepository;
        this.usuarioRepository = usuarioRepository;
        this.numeroOrdenGenerator = numeroOrdenGenerator;
        this.estadoOrdenStateResolver = estadoOrdenStateResolver;
        this.ordenCompraMapper = ordenCompraMapper;
        this.proveedorMapper = proveedorMapper;
        this.eventPublisher = eventPublisher;
        this.pdfRenderer = pdfRenderer;
    }

    /**
     * Crea una orden en estado CREADA, con su primer registro de auditoria.
     *
     * <p>Valida que la sucursal destino este activa, que el cliente sea obligatorio solo en Venta
     * Directa, que cada producto pertenezca a una categoria permitida para el formato de la sucursal
     * y que los precios negociados respeten los limites del proveedor. El numero de orden y el total
     * los calcula el servidor.
     *
     * @throws NegocioException si alguna regla de negocio se incumple (categoria no permitida,
     *     cliente obligatorio o no aplicable, sucursal inactiva, precio fuera de rango)
     * @throws RecursoNoEncontradoException si el proveedor, la sucursal, el cliente o un producto no existen
     */
    @Transactional
    public OrdenCompraResponse crearOrden(OrdenCompraRequest request, UsuarioPrincipal actor) {
        Proveedor proveedor = buscarProveedor(request.proveedorId());
        Sucursal sucursal = buscarSucursal(request.sucursalDestinoId());
        exigirSucursalActiva(sucursal);
        Cliente cliente = resolverCliente(request.clienteId(), sucursal);

        OrdenCompra orden = OrdenCompra.builder()
            .numeroOrden(numeroOrdenGenerator.generar())
            .proveedor(proveedor)
            .sucursalDestino(sucursal)
            .cliente(cliente)
            .usuario(usuarioRepository.getReferenceById(actor.id()))
            .estado(EstadoOrden.CREADA)
            .fechaNecesaria(request.fechaNecesaria())
            .build();

        agregarLineas(orden, request.detalle(), sucursal.getFormato(), proveedor, Map.of());
        orden.setTotal(calcularTotal(orden));

        OrdenCompra guardada = ordenCompraRepository.save(orden);
        registrarAuditoria(guardada, null, EstadoOrden.CREADA, actor, null);

        return ordenCompraMapper.toResponse(guardada);
    }

    /**
     * Devuelve una orden, con relaciones embebidas si se piden en {@code expand} (proveedor,
     * linea_tiempo).
     *
     * @throws AccessDeniedException si un GERENTE_SUCURSAL pide una orden de otra sucursal
     * @throws RecursoNoEncontradoException si la orden no existe
     */
    @Transactional(readOnly = true)
    public OrdenCompraResponse obtenerOrden(Integer id, UsuarioPrincipal actor, Set<String> expand) {
        return mapearConExpand(obtenerOrdenEntidadAutorizada(id, actor), expand);
    }

    /**
     * Devuelve la entidad completa de la orden tras verificar el acceso por sucursal, para quien
     * necesita navegar sus relaciones (por ejemplo, armar el PDF o el reporte Excel) en vez del DTO
     * aplanado de {@link #obtenerOrden}. Reutiliza esa misma verificacion para no duplicarla.
     *
     * <p>Quien la llame debe hacerlo dentro de su propia transaccion de solo lectura: las relaciones
     * son lazy y dejan de estar disponibles cuando se cierra la sesion.
     *
     * @throws AccessDeniedException si un GERENTE_SUCURSAL pide una orden de otra sucursal
     * @throws RecursoNoEncontradoException si la orden no existe
     */
    @Transactional(readOnly = true)
    public OrdenCompra obtenerOrdenEntidadAutorizada(Integer id, UsuarioPrincipal actor) {
        OrdenCompra orden = buscarOrdenOrLanzar(id);
        verificarAccesoPorSucursal(orden, actor);
        return orden;
    }

    /**
     * Lista ordenes con filtros opcionales y paginacion por cursor: {@code cursor} es el numero de
     * orden de la ultima fila de la pagina anterior, y la respuesta indica si hay otra pagina.
     *
     * <p>Un GERENTE_SUCURSAL queda restringido a su sucursal sin importar el filtro de sucursal que envie.
     *
     * @throws NegocioException si el rango de fechas esta invertido
     */
    @Transactional(readOnly = true)
    public ListEnvelope<OrdenCompraResponse> listar(
        EstadoOrden estado, Integer proveedorId, Integer sucursalId, Integer clienteId, String numeroOrden,
        LocalDate fechaDesde, LocalDate fechaHasta, String cursor, int limit, UsuarioPrincipal actor, Set<String> expand
    ) {
        validarRangoFechas(fechaDesde, fechaHasta);

        // GERENTE_SUCURSAL nunca ve otra sucursal aunque la pida por parametro: el filtro real lo
        // decide el backend a partir de su propio usuario, no lo que el cliente envie.
        Integer sucursalEfectiva = actor.rol() == Rol.GERENTE_SUCURSAL ? actor.sucursalId() : sucursalId;

        List<OrdenCompra> encontradas = ordenCompraRepository.buscar(estado, proveedorId, sucursalEfectiva,
            clienteId, numeroOrden, sinFiltroDesde(fechaDesde), sinFiltroHasta(fechaHasta), cursor,
            PageRequest.of(0, limit + 1));

        boolean hasMore = encontradas.size() > limit;
        List<OrdenCompra> pagina = hasMore ? encontradas.subList(0, limit) : encontradas;

        List<OrdenCompraResponse> data = pagina.stream().map(orden -> mapearConExpand(orden, expand)).toList();
        return ListEnvelope.of(data, hasMore);
    }

    // fecha_necesaria es NOT NULL en la base de datos (ver V1__init_schema.sql), asi que un
    // limite muy por fuera de cualquier fecha real hace que la comparacion nunca excluya
    // resultados: es el equivalente de "sin filtro" sin tener que pasarle null a la consulta.
    private static final LocalDate FECHA_MINIMA = LocalDate.of(1, 1, 1);
    private static final LocalDate FECHA_MAXIMA = LocalDate.of(9999, 12, 31);

    static LocalDate sinFiltroDesde(LocalDate fechaDesde) {
        return fechaDesde != null ? fechaDesde : FECHA_MINIMA;
    }

    static LocalDate sinFiltroHasta(LocalDate fechaHasta) {
        return fechaHasta != null ? fechaHasta : FECHA_MAXIMA;
    }

    // Cubre los tres casos validos (solo desde, solo hasta, o ambas) sin rechazar ninguno; solo
    // rechaza la combinacion sin sentido de un rango invertido.
    static void validarRangoFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new NegocioException("rango_fecha_invalido",
                "La fecha desde no puede ser posterior a la fecha hasta.", "fecha_desde");
        }
    }

    /**
     * Devuelve la linea de tiempo de una orden: sus cambios de estado internos junto con los eventos
     * que reporto el proveedor, ordenados por fecha. Es solo lectura: los eventos del proveedor nunca
     * cambian el estado de la orden.
     *
     * @throws AccessDeniedException si un GERENTE_SUCURSAL pide una orden de otra sucursal
     * @throws RecursoNoEncontradoException si la orden no existe
     */
    @Transactional(readOnly = true)
    public ListEnvelope<LineaTiempoItemResponse> obtenerLineaTiempo(Integer id, UsuarioPrincipal actor) {
        OrdenCompra orden = buscarOrdenOrLanzar(id);
        verificarAccesoPorSucursal(orden, actor);
        return ListEnvelope.of(construirLineaTiempo(id), false);
    }

    private OrdenCompraResponse mapearConExpand(OrdenCompra orden, Set<String> expand) {
        OrdenCompraResponse response = ordenCompraMapper.toResponse(orden);
        if (expand != null && expand.contains("proveedor")) {
            response = response.conProveedorExpandido(proveedorMapper.toResponse(orden.getProveedor()));
        }
        if (expand != null && expand.contains("linea_tiempo")) {
            response = response.conLineaTiempoExpandida(construirLineaTiempo(orden.getId()));
        }
        return response;
    }

    // Combina auditoria interna (cambios de estado) con eventos del proveedor (webhook),
    // ordenados por fecha - la vista unica del ciclo de vida completo de la orden.
    // Puramente de lectura: el webhook nunca dispara transiciones de estado.
    private List<LineaTiempoItemResponse> construirLineaTiempo(Integer ordenId) {
        Stream<LineaTiempoItemResponse> internos = auditoriaRepository.findByOrdenCompraIdOrderByFechaAsc(ordenId)
            .stream()
            .map(a -> new LineaTiempoItemResponse("interno", a.getEstadoNuevo().name(), a.getObservacion(), a.getFecha()));

        Stream<LineaTiempoItemResponse> deProveedor = eventoProveedorRepository
            .findByOrdenCompraIdOrderByFechaEventoAsc(ordenId).stream()
            .map(this::aLineaTiempoItem);

        return Stream.concat(internos, deProveedor)
            .sorted(Comparator.comparing(LineaTiempoItemResponse::fecha))
            .toList();
    }

    private LineaTiempoItemResponse aLineaTiempoItem(OrdenEventoProveedor evento) {
        return new LineaTiempoItemResponse("proveedor", evento.getTipoEvento().name(),
            evento.getObservacion(), evento.getFechaEvento());
    }

    /**
     * Reemplaza los datos y las lineas de una orden que sigue en CREADA.
     *
     * <p>Las lineas se reconstruyen, pero cada una conserva lo que ya tenia: un producto que ya estaba
     * en la orden mantiene su precio de catalogo de referencia y, si la peticion no trae precio, su
     * precio; un producto nuevo toma el catalogo vigente. Un precio que difiere del catalogo se valida
     * contra los limites del proveedor de la orden, tambien cuando se cambia de proveedor. Una
     * sucursal inactiva solo se rechaza si se elige como destino nuevo.
     *
     * @throws EstadoInvalidoException si la orden ya no esta en CREADA
     * @throws NegocioException si alguna regla de negocio se incumple
     */
    @Transactional
    public OrdenCompraResponse actualizarDetalle(Integer id, OrdenCompraRequest request, UsuarioPrincipal actor) {
        OrdenCompra orden = buscarOrdenOrLanzar(id);

        if (orden.getEstado() != EstadoOrden.CREADA) {
            throw new EstadoInvalidoException("Solo se puede modificar el detalle de una orden en estado CREADA.");
        }

        Proveedor proveedor = buscarProveedor(request.proveedorId());
        Sucursal sucursal = buscarSucursal(request.sucursalDestinoId());
        // Una orden ya creada para una sucursal que luego se desactivo se puede seguir editando; lo que
        // no se permite es elegir una sucursal inactiva como destino nuevo.
        if (!sucursal.getId().equals(orden.getSucursalDestino().getId())) {
            exigirSucursalActiva(sucursal);
        }
        Cliente cliente = resolverCliente(request.clienteId(), sucursal);

        orden.setProveedor(proveedor);
        orden.setSucursalDestino(sucursal);
        orden.setCliente(cliente);
        orden.setFechaNecesaria(request.fechaNecesaria());

        // Las lineas se reconstruyen: se recuerdan los precios de las que ya existian para que una linea
        // sin negociar conserve su precio y la referencia de catalogo contra la que se validan los limites.
        Map<Integer, PrecioGuardado> preciosGuardados = preciosGuardados(orden);
        orden.getDetalle().clear();
        agregarLineas(orden, request.detalle(), sucursal.getFormato(), proveedor, preciosGuardados);
        orden.setTotal(calcularTotal(orden));
        orden.setModificadoPor(usuarioRepository.getReferenceById(actor.id()));

        return ordenCompraMapper.toResponse(orden);
    }

    /**
     * Aprueba una orden CREADA y avisa al proveedor por correo, con el PDF adjunto, una vez que la
     * transaccion se confirma.
     *
     * @throws EstadoInvalidoException si la orden no esta en CREADA
     */
    @Transactional
    public OrdenCompraResponse aprobar(Integer id, UsuarioPrincipal actor) {
        OrdenCompra orden = buscarOrdenOrLanzar(id);
        EstadoOrden anterior = orden.getEstado();
        estadoOrdenStateResolver.resolver(anterior).aprobar(orden);
        registrarAuditoria(orden, anterior, orden.getEstado(), actor, null);
        eventPublisher.publishEvent(construirEventoAprobada(orden));
        return ordenCompraMapper.toResponse(orden);
    }

    /**
     * Anula una orden CREADA o APROBADA, registrando el motivo (la peticion web exige que no este
     * vacio). Una orden cerrada no se puede anular: la mercaderia ya ingreso a la sucursal y anularla
     * dejaria el inventario inconsistente con la orden.
     *
     * <p>El proveedor solo recibe el aviso de anulacion si la orden ya estaba APROBADA.
     *
     * @throws EstadoInvalidoException si la transicion no es valida desde el estado actual
     */
    @Transactional
    public OrdenCompraResponse anular(Integer id, String motivo, UsuarioPrincipal actor) {
        OrdenCompra orden = buscarOrdenOrLanzar(id);
        EstadoOrden anterior = orden.getEstado();
        estadoOrdenStateResolver.resolver(anterior).anular(orden, motivo);
        registrarAuditoria(orden, anterior, orden.getEstado(), actor, motivo);
        // El proveedor solo se entera de una orden cuando se aprueba; si se anula una que seguia en
        // CREADA, nunca supo de ella, asi que no hay nada que cancelarle y notificarlo seria confuso.
        if (anterior == EstadoOrden.APROBADA) {
            eventPublisher.publishEvent(construirEventoAnulada(orden));
        }
        return ordenCompraMapper.toResponse(orden);
    }

    /**
     * Confirma la recepcion de la mercaderia y cierra una orden APROBADA, indicando si llego conforme.
     *
     * <p>Quien certifica la recepcion no es quien aprueba el gasto: un GERENTE_SUCURSAL solo puede
     * cerrar ordenes de su propia sucursal, y el rol COMPRADOR queda excluido por la capa web.
     *
     * @throws AccessDeniedException si un GERENTE_SUCURSAL intenta cerrar una orden de otra sucursal
     * @throws EstadoInvalidoException si la orden no esta en APROBADA
     */
    @Transactional
    public OrdenCompraResponse cerrar(Integer id, boolean conforme, String observacion, UsuarioPrincipal actor) {
        OrdenCompra orden = buscarOrdenOrLanzar(id);

        if (actor.rol() == Rol.GERENTE_SUCURSAL
                && !orden.getSucursalDestino().getId().equals(actor.sucursalId())) {
            throw new AccessDeniedException("Solo puede cerrar ordenes de su propia sucursal.");
        }

        EstadoOrden anterior = orden.getEstado();
        estadoOrdenStateResolver.resolver(anterior).cerrar(orden, conforme, observacion);
        registrarAuditoria(orden, anterior, orden.getEstado(), actor, observacion);
        return ordenCompraMapper.toResponse(orden);
    }

    private Proveedor buscarProveedor(Integer proveedorId) {
        return proveedorRepository.findById(proveedorId)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "proveedor_no_encontrado", "No existe el proveedor indicado.", "proveedor_id"));
    }

    private Sucursal buscarSucursal(Integer sucursalId) {
        return sucursalRepository.findById(sucursalId)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "sucursal_no_encontrada", "No existe la sucursal destino indicada.", "sucursal_destino_id"));
    }

    private void exigirSucursalActiva(Sucursal sucursal) {
        if (!Boolean.TRUE.equals(sucursal.getActivo())) {
            throw new NegocioException("sucursal_inactiva",
                "La sucursal destino esta inactiva y no puede recibir ordenes nuevas.", "sucursal_destino_id");
        }
    }

    private OrdenCompra buscarOrdenOrLanzar(Integer id) {
        return ordenCompraRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "orden_no_encontrada", "No existe la orden de compra indicada.", "id"));
    }

    private void verificarAccesoPorSucursal(OrdenCompra orden, UsuarioPrincipal actor) {
        if (actor.rol() == Rol.GERENTE_SUCURSAL
                && !orden.getSucursalDestino().getId().equals(actor.sucursalId())) {
            throw new AccessDeniedException("No tiene acceso a ordenes de otra sucursal.");
        }
    }

    private Cliente resolverCliente(Integer clienteId, Sucursal sucursal) {
        boolean ventaDirecta = sucursal.getFormato() == FormatoSucursal.VENTA_DIRECTA;

        if (ventaDirecta) {
            if (clienteId == null) {
                throw new NegocioException("cliente_obligatorio",
                    "El cliente es obligatorio cuando la sucursal destino es de Venta Directa.", "cliente_id");
            }
            return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                    "cliente_no_encontrado", "No existe el cliente indicado.", "cliente_id"));
        }

        if (clienteId != null) {
            throw new NegocioException("cliente_no_aplica",
                "El cliente no aplica para sucursales de formato %s.".formatted(sucursal.getFormato()), "cliente_id");
        }

        return null;
    }

    // Precios que una linea ya tenia en la orden antes de reconstruir el detalle al editarla.
    private record PrecioGuardado(BigDecimal catalogo, BigDecimal unitario) {
    }

    private Map<Integer, PrecioGuardado> preciosGuardados(OrdenCompra orden) {
        Map<Integer, PrecioGuardado> precios = new HashMap<>();
        for (OrdenCompraDetalle linea : orden.getDetalle()) {
            precios.put(linea.getProducto().getId(), new PrecioGuardado(linea.getPrecioCatalogo(), linea.getPrecioUnitario()));
        }
        return precios;
    }

    private void agregarLineas(
        OrdenCompra orden, List<DetalleRequest> lineas, FormatoSucursal formato,
        Proveedor proveedor, Map<Integer, PrecioGuardado> preciosGuardados
    ) {
        Set<Integer> categoriasPermitidas = formatoCategoriaPermitidaRepository.idsDeCategoriasPermitidas(formato);

        for (int i = 0; i < lineas.size(); i++) {
            DetalleRequest lineaRequest = lineas.get(i);
            String paramLinea = "detalle[%d].producto_id".formatted(i);

            Producto producto = productoRepository.findById(lineaRequest.productoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                    "producto_no_encontrado", "No existe el producto indicado.", paramLinea));

            if (!categoriasPermitidas.contains(producto.getCategoria().getId())) {
                throw new NegocioException("categoria_no_permitida",
                    "El producto %s (categoria %s) no esta permitido para sucursales de formato %s."
                        .formatted(producto.getNombre(), producto.getCategoria().getNombre(), formato),
                    paramLinea);
            }

            // Referencia de catalogo: la que la linea ya tenia (al editar), o la vigente si es nueva. Asi
            // una orden vieja no se bloquea ni cambia de precio porque el catalogo se haya movido.
            PrecioGuardado guardado = preciosGuardados.get(producto.getId());
            BigDecimal precioCatalogo = guardado != null ? guardado.catalogo() : producto.getPrecio();

            // Precio pedido explicitamente; si no viene, la linea conserva el suyo (edicion) o toma el
            // de catalogo (nueva).
            BigDecimal precioUnitario = lineaRequest.precioUnitario() != null
                ? lineaRequest.precioUnitario().setScale(2, RoundingMode.HALF_UP)
                : (guardado != null ? guardado.unitario() : precioCatalogo);

            // Se valida siempre que difiera del catalogo, tambien cuando la linea conserva su precio: si
            // al editar se cambio de proveedor, los limites del nuevo pueden no admitirlo.
            if (precioUnitario.compareTo(precioCatalogo) != 0) {
                validarPrecioNegociado(precioUnitario, precioCatalogo, proveedor, producto,
                    "detalle[%d].precio_unitario".formatted(i));
            }

            BigDecimal subtotal = precioUnitario.multiply(lineaRequest.cantidad()).setScale(2, RoundingMode.HALF_UP);

            OrdenCompraDetalle linea = OrdenCompraDetalle.builder()
                .producto(producto)
                .cantidad(lineaRequest.cantidad())
                .precioUnitario(precioUnitario)
                .precioCatalogo(precioCatalogo)
                .subtotal(subtotal)
                .build();

            orden.agregarDetalle(linea);
        }
    }

    // Limites inclusivos: [catalogo x (1 - descuento), catalogo x (1 + aumento)], calculados exactos
    // con BigDecimal (sin redondear) y solo redondeados al mostrarlos en el mensaje.
    private void validarPrecioNegociado(
        BigDecimal precio, BigDecimal precioCatalogo, Proveedor proveedor, Producto producto, String param
    ) {
        BigDecimal cien = BigDecimal.valueOf(100);
        BigDecimal descuento = proveedor.getDescuentoMaximoPct();
        BigDecimal aumento = proveedor.getAumentoMaximoPct();

        if (descuento.signum() == 0 && aumento.signum() == 0) {
            throw new NegocioException("precio_fuera_de_rango",
                "El proveedor %s no admite negociar precios: %s se compra al precio de catalogo (%s)."
                    .formatted(proveedor.getNombre(), producto.getCodigo(), precioCatalogo.toPlainString()),
                param);
        }

        BigDecimal minimo = precioCatalogo.multiply(cien.subtract(descuento)).divide(cien);
        BigDecimal maximo = precioCatalogo.multiply(cien.add(aumento)).divide(cien);
        if (precio.compareTo(minimo) < 0 || precio.compareTo(maximo) > 0) {
            throw new NegocioException("precio_fuera_de_rango",
                ("El precio %s de %s esta fuera del rango que admite el proveedor %s: entre %s y %s "
                    + "(catalogo %s, descuento maximo %s%%, aumento maximo %s%%).")
                    .formatted(precio.toPlainString(), producto.getCodigo(), proveedor.getNombre(),
                        minimo.setScale(2, RoundingMode.CEILING).toPlainString(),
                        maximo.setScale(2, RoundingMode.FLOOR).toPlainString(),
                        precioCatalogo.toPlainString(), descuento.stripTrailingZeros().toPlainString(),
                        aumento.stripTrailingZeros().toPlainString()),
                param);
        }
    }

    private OrdenAprobadaEvent construirEventoAprobada(OrdenCompra orden) {
        List<OrdenAprobadaEvent.Linea> lineas = orden.getDetalle().stream()
            .map(linea -> new OrdenAprobadaEvent.Linea(
                linea.getProducto().getNombre(), linea.getCantidad(), linea.getProducto().getUnidadCompra()))
            .toList();

        return new OrdenAprobadaEvent(orden.getNumeroOrden(), orden.getProveedor().getEmail(),
            orden.getProveedor().getNombre(), orden.getFechaNecesaria(), orden.getTotal(), lineas,
            renderizarPdfParaNotificacion(orden));
    }

    private OrdenAnuladaEvent construirEventoAnulada(OrdenCompra orden) {
        return new OrdenAnuladaEvent(orden.getNumeroOrden(), orden.getProveedor().getEmail(),
            orden.getProveedor().getNombre(), orden.getMotivoAnulacion(), Instant.now(),
            renderizarPdfParaNotificacion(orden));
    }

    // Se renderiza aqui, todavia dentro de la transaccion que aprobo/anulo la orden (relaciones
    // lazy vivas), porque el EmailListener corre async despues del commit sin sesion de Hibernate.
    // Si el PDF falla, se registra el error pero no se bloquea la transicion de estado ni el correo
    // en si - el adjunto es una mejora sobre la notificacion, no un requisito para que exista.
    private byte[] renderizarPdfParaNotificacion(OrdenCompra orden) {
        try {
            return pdfRenderer.renderizar(orden);
        } catch (RuntimeException ex) {
            log.error("No se pudo generar el PDF adjunto para la orden {}: {}", orden.getNumeroOrden(), ex.getMessage(), ex);
            return null;
        }
    }

    private BigDecimal calcularTotal(OrdenCompra orden) {
        return orden.getDetalle().stream()
            .map(OrdenCompraDetalle::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private void registrarAuditoria(
        OrdenCompra orden, EstadoOrden anterior, EstadoOrden nuevo, UsuarioPrincipal actor, String observacion
    ) {
        OrdenCompraAuditoria auditoria = OrdenCompraAuditoria.builder()
            .ordenCompra(orden)
            .estadoAnterior(anterior)
            .estadoNuevo(nuevo)
            .usuario(usuarioRepository.getReferenceById(actor.id()))
            .observacion(observacion)
            .build();
        auditoriaRepository.save(auditoria);
    }
}
