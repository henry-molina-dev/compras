package com.ferrocompras.ordenescompra.security;

import com.ferrocompras.ordenescompra.shared.entity.Usuario;
import com.ferrocompras.ordenescompra.shared.enums.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration-seconds:28800}") long expirationSeconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        Integer sucursalId = usuario.getSucursal() != null ? usuario.getSucursal().getId() : null;

        return Jwts.builder()
            .subject(usuario.getUsername())
            .claim("uid", usuario.getId())
            .claim("rol", usuario.getRol().name())
            .claim("sucursalId", sucursalId)
            .issuedAt(Date.from(ahora))
            .expiration(Date.from(ahora.plusSeconds(expirationSeconds)))
            .signWith(key)
            .compact();
    }

    public Optional<UsuarioPrincipal> validarYExtraerPrincipal(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            UsuarioPrincipal principal = new UsuarioPrincipal(
                asInteger(claims.get("uid")),
                claims.getSubject(),
                Rol.valueOf(claims.get("rol", String.class)),
                asInteger(claims.get("sucursalId"))
            );
            return Optional.of(principal);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Integer asInteger(Object claim) {
        return claim instanceof Number number ? number.intValue() : null;
    }
}
