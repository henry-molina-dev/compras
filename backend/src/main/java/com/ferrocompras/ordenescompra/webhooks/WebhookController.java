package com.ferrocompras.ordenescompra.webhooks;

import com.ferrocompras.ordenescompra.dto.EventoProveedorRequest;
import com.ferrocompras.ordenescompra.dto.EventoProveedorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/eventos")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<EventoProveedorResponse> registrarEvento(
        @Valid @RequestBody EventoProveedorRequest request,
        @AuthenticationPrincipal ProveedorPrincipal autenticado
    ) {
        EventoProveedorResponse creado = webhookService.registrarEvento(request, autenticado);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }
}
