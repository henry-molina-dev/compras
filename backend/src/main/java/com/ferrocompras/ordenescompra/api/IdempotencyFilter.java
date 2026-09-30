package com.ferrocompras.ordenescompra.api;

import com.ferrocompras.ordenescompra.shared.entity.IdempotencyKey;
import com.ferrocompras.ordenescompra.webhooks.ProveedorPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

// No forma parte de SecurityConfig a proposito: Spring Boot registra los filtros propios (orden
// por defecto Ordered.LOWEST_PRECEDENCE) despues de la cadena de Spring Security (orden temprano,
// negativo), asi que esto solo corre una vez que la request ya paso autenticacion y el chequeo
// grueso de "debe estar autenticado". El chequeo de rol especifico (@PreAuthorize) ocurre recien
// al invocar el metodo del controlador, un paso mas tarde que este filtro - una respuesta cacheada
// podria en teoria devolverse a un usuario autenticado sin el rol correcto si de alguna forma
// obtuviera la Idempotency-Key ajena; se documenta como limite conocido, no se resuelve aqui.
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String clave = request.getHeader(HEADER);

        if (!aplica(request, clave)) {
            filterChain.doFilter(request, response);
            return;
        }

        String endpoint = request.getMethod() + " " + request.getRequestURI() + alcanceDelProveedor();
        Optional<IdempotencyKey> existente = idempotencyService.buscarVigente(clave, endpoint);

        if (existente.isPresent()) {
            reproducirRespuesta(response, existente.get());
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrapper);

        idempotencyService.guardar(clave, endpoint, wrapper.getStatus(),
            new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8));
        wrapper.copyBodyToResponse();
    }

    // El webhook identifica al proveedor por su API key, no por la URL, asi que la URL sola no separa
    // a un proveedor de otro: sin esto, la misma Idempotency-Key usada por dos proveedores
    // colisionaria y uno recibiria la respuesta cacheada del otro. Por eso el alcance de la clave
    // incluye al proveedor autenticado.
    private String alcanceDelProveedor() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof ProveedorPrincipal proveedor) {
            return " proveedor:" + proveedor.id();
        }
        return "";
    }

    private boolean aplica(HttpServletRequest request, String clave) {
        if (clave == null || clave.isBlank()) {
            return false;
        }
        String metodo = request.getMethod();
        boolean metodoMutable = HttpMethod.POST.matches(metodo) || HttpMethod.PATCH.matches(metodo);
        String uri = request.getRequestURI();
        // Rutas que aceptan Idempotency-Key en sus POST/PATCH: las ordenes, el webhook (los
        // proveedores reintentan) y el alta de proveedores y productos. El alta de clientes y usuarios
        // no declara la clave, por eso no se cubre.
        return metodoMutable && (uri.startsWith("/api/ordenes") || uri.startsWith("/api/webhooks")
            || uri.startsWith("/api/proveedores") || uri.startsWith("/api/productos"));
    }

    private void reproducirRespuesta(HttpServletResponse response, IdempotencyKey guardado) throws IOException {
        response.setStatus(guardado.getStatusCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(guardado.getResponseBody());
    }
}
