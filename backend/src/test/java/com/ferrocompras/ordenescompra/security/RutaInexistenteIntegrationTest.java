package com.ferrocompras.ordenescompra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Deliberadamente NO usa MockMvc (como el resto de los tests de este modulo): MockMvc no reproduce
// el forward real de un servlet container a /error cuando ninguna ruta coincide, asi que un test
// con MockMvc pasaria igual con o sin el permitAll de /error en SecurityConfig y no detectaria una
// regresion. Este test arranca un Tomcat embebido de verdad (RANDOM_PORT) y hace la request por
// HTTP real, igual que el bug se reprodujo originalmente contra el contenedor Docker.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RutaInexistenteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int puerto;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void unaRutaInexistenteConTokenValidoDevuelve404NoUn401() {
        String loginJson = restTemplate.postForObject(
            "http://localhost:%d/api/auth/login".formatted(puerto),
            new HttpEntity<>("""
                {"username": "admin", "password": "Compras2026!"}
                """, jsonHeaders()),
            String.class);
        String token = JsonPath.read(loginJson, "$.access_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        // Regresion: /error no estaba permitido en la configuracion de seguridad, asi que el
        // forward interno de Spring Boot para renderizar un 404 se topaba con
        // "anyRequest().authenticated()" y devolvia un 401 enganoso "token invalido" en vez del
        // 404 real - con un token perfectamente valido.
        ResponseEntity<String> respuesta = restTemplate.exchange(
            "http://localhost:%d/api/esto-no-existe".formatted(puerto),
            HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
