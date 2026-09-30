package com.ferrocompras.ordenescompra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

// @EnableAsync habilita el envio de correo en un hilo aparte (notificaciones/EmailListener), para
// que nunca bloquee ni participe en la transaccion del cambio de estado que lo dispara.
@EnableAsync
@SpringBootApplication
public class OrdenesCompraApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdenesCompraApplication.class, args);
    }
}
