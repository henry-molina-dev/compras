package com.ferrocompras.ordenescompra.moduloadministracion.service;

import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.ProductoRequest;
import com.ferrocompras.ordenescompra.dto.ProductoResponse;
import com.ferrocompras.ordenescompra.dto.mapper.ProductoMapper;
import com.ferrocompras.ordenescompra.exception.RecursoNoEncontradoException;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import com.ferrocompras.ordenescompra.shared.entity.CategoriaProducto;
import com.ferrocompras.ordenescompra.shared.entity.Producto;
import com.ferrocompras.ordenescompra.shared.repository.CategoriaProductoRepository;
import com.ferrocompras.ordenescompra.shared.repository.FormatoCategoriaPermitidaRepository;
import com.ferrocompras.ordenescompra.shared.repository.ProductoRepository;
import com.ferrocompras.ordenescompra.shared.repository.SucursalRepository;
import com.ferrocompras.ordenescompra.shared.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaProductoRepository categoriaProductoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final FormatoCategoriaPermitidaRepository formatoCategoriaPermitidaRepository;
    private final ProductoMapper productoMapper;

    public ProductoService(
        ProductoRepository productoRepository,
        CategoriaProductoRepository categoriaProductoRepository,
        UsuarioRepository usuarioRepository,
        SucursalRepository sucursalRepository,
        FormatoCategoriaPermitidaRepository formatoCategoriaPermitidaRepository,
        ProductoMapper productoMapper
    ) {
        this.productoRepository = productoRepository;
        this.categoriaProductoRepository = categoriaProductoRepository;
        this.usuarioRepository = usuarioRepository;
        this.sucursalRepository = sucursalRepository;
        this.formatoCategoriaPermitidaRepository = formatoCategoriaPermitidaRepository;
        this.productoMapper = productoMapper;
    }

    /**
     * Lista el catalogo, opcionalmente acotado a una categoria y/o a los productos que la sucursal
     * indicada puede pedir segun el formato de esa sucursal (las mismas categorias que la creacion
     * de ordenes acepta), de modo que quien arma una orden solo vea opciones validas.
     */
    public ListEnvelope<ProductoResponse> listar(Integer categoriaId, Integer sucursalId) {
        List<Producto> productos = categoriaId == null
            ? productoRepository.findAll()
            : productoRepository.findByCategoriaId(categoriaId);
        if (sucursalId != null) {
            var sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                    "sucursal_no_encontrada", "La sucursal indicada no existe.", "sucursal_id"));
            Set<Integer> categoriasPermitidas = formatoCategoriaPermitidaRepository.idsDeCategoriasPermitidas(sucursal.getFormato());
            productos = productos.stream()
                .filter(p -> categoriasPermitidas.contains(p.getCategoria().getId()))
                .toList();
        }
        List<ProductoResponse> data = productos.stream().map(productoMapper::toResponse).toList();
        return ListEnvelope.of(data, false);
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest request, UsuarioPrincipal actor) {
        Producto producto = productoMapper.toEntity(request);
        producto.setCategoria(buscarCategoria(request.categoriaId()));
        // openapi.yaml documenta factor_conversion con default: 1, pero toEntity (constructor sin
        // argumentos + setters) no aplica el @Builder.Default de la entidad - solo el builder lo
        // hace. Sin este ajuste, omitir el campo lo dejaria en null en vez de 1.
        if (producto.getFactorConversion() == null) {
            producto.setFactorConversion(BigDecimal.ONE);
        }
        producto.setActivo(request.activo() == null || request.activo());
        producto.setCreadoPor(usuarioRepository.getReferenceById(actor.id()));
        producto.setModificadoPor(usuarioRepository.getReferenceById(actor.id()));
        return productoMapper.toResponse(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse actualizar(Integer id, ProductoRequest request, UsuarioPrincipal actor) {
        Producto producto = buscarOrLanzar(id);
        producto.setCodigo(request.codigo());
        producto.setNombre(request.nombre());
        producto.setCategoria(buscarCategoria(request.categoriaId()));
        producto.setPrecio(request.precio());
        producto.setUnidadVenta(request.unidadVenta());
        producto.setUnidadCompra(request.unidadCompra());
        if (request.factorConversion() != null) {
            producto.setFactorConversion(request.factorConversion());
        }
        if (request.activo() != null) {
            producto.setActivo(request.activo());
        }
        producto.setModificadoPor(usuarioRepository.getReferenceById(actor.id()));
        return productoMapper.toResponse(productoRepository.save(producto));
    }

    private CategoriaProducto buscarCategoria(Integer categoriaId) {
        return categoriaProductoRepository.findById(categoriaId)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "categoria_no_encontrada", "No existe la categoria indicada.", "categoria_id"));
    }

    private Producto buscarOrLanzar(Integer id) {
        return productoRepository.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "producto_no_encontrado", "No existe el producto indicado.", "id"));
    }
}
