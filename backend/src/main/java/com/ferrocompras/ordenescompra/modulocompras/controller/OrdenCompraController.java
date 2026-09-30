package com.ferrocompras.ordenescompra.modulocompras.controller;

import com.ferrocompras.ordenescompra.dto.AnularRequest;
import com.ferrocompras.ordenescompra.dto.CerrarRequest;
import com.ferrocompras.ordenescompra.dto.ImportacionLoteResponse;
import com.ferrocompras.ordenescompra.dto.LineaTiempoItemResponse;
import com.ferrocompras.ordenescompra.dto.ListEnvelope;
import com.ferrocompras.ordenescompra.dto.OrdenCompraRequest;
import com.ferrocompras.ordenescompra.dto.OrdenCompraResponse;
import com.ferrocompras.ordenescompra.exception.NegocioException;
import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.service.ExportacionOrdenesService;
import com.ferrocompras.ordenescompra.modulocompras.service.ImportacionOrdenesService;
import com.ferrocompras.ordenescompra.modulocompras.service.OrdenCompraService;
import com.ferrocompras.ordenescompra.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ordenes")
@Validated
public class OrdenCompraController {

    private final OrdenCompraService ordenCompraService;
    private final ImportacionOrdenesService importacionOrdenesService;
    private final ExportacionOrdenesService exportacionOrdenesService;

    public OrdenCompraController(
        OrdenCompraService ordenCompraService,
        ImportacionOrdenesService importacionOrdenesService,
        ExportacionOrdenesService exportacionOrdenesService
    ) {
        this.ordenCompraService = ordenCompraService;
        this.importacionOrdenesService = importacionOrdenesService;
        this.exportacionOrdenesService = exportacionOrdenesService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR','GERENTE_SUCURSAL')")
    public ListEnvelope<OrdenCompraResponse> listar(
        @RequestParam(required = false) EstadoOrden estado,
        @RequestParam(name = "proveedor_id", required = false) Integer proveedorId,
        @RequestParam(name = "sucursal_id", required = false) Integer sucursalId,
        @RequestParam(name = "cliente_id", required = false) Integer clienteId,
        @RequestParam(name = "numero_orden", required = false) String numeroOrden,
        @RequestParam(name = "fecha_desde", required = false) LocalDate fechaDesde,
        @RequestParam(name = "fecha_hasta", required = false) LocalDate fechaHasta,
        @RequestParam(name = "starting_after", required = false) String startingAfter,
        @RequestParam(defaultValue = "20") @Max(100) int limit,
        @RequestParam(required = false) List<String> expand,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return ordenCompraService.listar(estado, proveedorId, sucursalId, clienteId, numeroOrden, fechaDesde,
            fechaHasta, startingAfter, limit, actor, aSet(expand));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR')")
    public ResponseEntity<OrdenCompraResponse> crear(
        @Valid @RequestBody OrdenCompraRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        OrdenCompraResponse creada = ordenCompraService.crearOrden(request, actor);
        return ResponseEntity.created(URI.create("/api/ordenes/" + creada.id())).body(creada);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR','GERENTE_SUCURSAL')")
    public OrdenCompraResponse obtener(
        @PathVariable Integer id,
        @RequestParam(required = false) List<String> expand,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return ordenCompraService.obtenerOrden(id, actor, aSet(expand));
    }

    @GetMapping("/{id}/linea-tiempo")
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR','GERENTE_SUCURSAL')")
    public ListEnvelope<LineaTiempoItemResponse> lineaTiempo(
        @PathVariable Integer id,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return ordenCompraService.obtenerLineaTiempo(id, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR')")
    public OrdenCompraResponse actualizar(
        @PathVariable Integer id,
        @Valid @RequestBody OrdenCompraRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return ordenCompraService.actualizarDetalle(id, request, actor);
    }

    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR')")
    public OrdenCompraResponse aprobar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioPrincipal actor) {
        return ordenCompraService.aprobar(id, actor);
    }

    @PatchMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR')")
    public OrdenCompraResponse anular(
        @PathVariable Integer id,
        @Valid @RequestBody AnularRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return ordenCompraService.anular(id, request.motivo(), actor);
    }

    @PatchMapping("/{id}/cerrar")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE_SUCURSAL')")
    public OrdenCompraResponse cerrar(
        @PathVariable Integer id,
        @Valid @RequestBody CerrarRequest request,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return ordenCompraService.cerrar(id, request.conforme(), request.observacion(), actor);
    }

    @GetMapping("/{id}/exportar")
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR','GERENTE_SUCURSAL')")
    public ResponseEntity<byte[]> exportarOrden(
        @PathVariable Integer id,
        @RequestParam String formato,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        if (!"pdf".equalsIgnoreCase(formato)) {
            throw new NegocioException(
                "formato_no_soportado", "El unico formato soportado para una orden individual es 'pdf'.", "formato");
        }
        ExportacionOrdenesService.PdfExportado exportado = exportacionOrdenesService.exportarPdf(id, actor);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(exportado.numeroOrden() + ".pdf", StandardCharsets.UTF_8).build().toString())
            .body(exportado.contenido());
    }

    @GetMapping("/exportar")
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR','GERENTE_SUCURSAL')")
    public ResponseEntity<byte[]> exportarListado(
        @RequestParam String formato,
        @RequestParam(required = false) EstadoOrden estado,
        @RequestParam(name = "proveedor_id", required = false) Integer proveedorId,
        @RequestParam(name = "sucursal_id", required = false) Integer sucursalId,
        @RequestParam(name = "cliente_id", required = false) Integer clienteId,
        @RequestParam(name = "numero_orden", required = false) String numeroOrden,
        @RequestParam(name = "fecha_desde", required = false) LocalDate fechaDesde,
        @RequestParam(name = "fecha_hasta", required = false) LocalDate fechaHasta,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        if (!"xlsx".equalsIgnoreCase(formato)) {
            throw new NegocioException(
                "formato_no_soportado", "El unico formato soportado para el listado es 'xlsx'.", "formato");
        }
        byte[] excel = exportacionOrdenesService.exportarExcel(
            estado, proveedorId, sucursalId, clienteId, numeroOrden, fechaDesde, fechaHasta, actor);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("ordenes.xlsx", StandardCharsets.UTF_8).build().toString())
            .body(excel);
    }

    @GetMapping("/importaciones/plantilla")
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR')")
    public ResponseEntity<byte[]> plantillaImportacion() {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("plantilla_ordenes.xlsx", StandardCharsets.UTF_8).build().toString())
            .body(importacionOrdenesService.generarPlantilla());
    }

    @PostMapping(value = "/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR')")
    public ImportacionLoteResponse importar(
        @RequestParam("archivo") MultipartFile archivo,
        @AuthenticationPrincipal UsuarioPrincipal actor
    ) {
        return importacionOrdenesService.importar(archivo, actor);
    }

    private Set<String> aSet(List<String> expand) {
        return expand == null ? Set.of() : Set.copyOf(expand);
    }
}
