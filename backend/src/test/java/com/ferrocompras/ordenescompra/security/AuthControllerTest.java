package com.ferrocompras.ordenescompra.security;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginConCredencialesValidasDevuelveUnJwt() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("admin", "Compras2026!")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").value(notNullValue()))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void loginConPasswordIncorrectoDevuelve401ConErrorTipado() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("admin", "password-incorrecto")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.type").value("authentication_error"))
            .andExpect(jsonPath("$.error.code").value("credenciales_invalidas"));
    }

    @Test
    void unaRutaProtegidaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/ordenes"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.type").value("authentication_error"));
    }

    @Test
    void unTokenValidoAtraviesaLaAutenticacion() throws Exception {
        String loginJson = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("admin", "Compras2026!")))
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(loginJson, "$.access_token");

        // ADMIN tiene acceso de lectura a /api/ordenes: un 200 (en vez del 401 del test anterior,
        // sin token) confirma que el filtro de JWT si autentico la request.
        mockMvc.perform(get("/api/ordenes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }

    private String loginBody(String username, String password) {
        return """
            {"username": "%s", "password": "%s"}
            """.formatted(username, password);
    }
}
