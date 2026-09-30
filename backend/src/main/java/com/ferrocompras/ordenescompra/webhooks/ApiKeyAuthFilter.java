package com.ferrocompras.ordenescompra.webhooks;

import com.ferrocompras.ordenescompra.shared.repository.ProveedorRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Cadena de seguridad independiente de JwtAuthFilter (ver SecurityConfig): el proveedor es un
// sistema externo autenticado por credencial propia, no un usuario interno con sesion. Solo
// autentica "existe un proveedor con esta api key"; el proveedor que actua es siempre el dueno de la
// api key (la URL no lleva id de proveedor) y WebhookService solo le deja tocar sus propias ordenes.
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Api-Key";

    private final ProveedorRepository proveedorRepository;

    public ApiKeyAuthFilter(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER);

        if (apiKey != null && !apiKey.isBlank()) {
            proveedorRepository.findByWebhookApiKey(apiKey).ifPresent(proveedor -> {
                ProveedorPrincipal principal = new ProveedorPrincipal(proveedor.getId(), proveedor.getNombre());
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_PROVEEDOR"));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
