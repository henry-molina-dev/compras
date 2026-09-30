package com.ferrocompras.ordenescompra.security;

import com.ferrocompras.ordenescompra.dto.LoginResponse;
import com.ferrocompras.ordenescompra.shared.entity.Usuario;
import com.ferrocompras.ordenescompra.shared.repository.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String CREDENCIALES_INVALIDAS = "Usuario o contrasena invalidos.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        Usuario usuario = usuarioRepository.findByUsername(username)
            .filter(Usuario::getActivo)
            .orElseThrow(() -> new BadCredentialsException(CREDENCIALES_INVALIDAS));

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }

        String token = jwtService.generarToken(usuario);
        return new LoginResponse("login_response", token, "Bearer", jwtService.expirationSeconds(), usuario.getRol());
    }
}
