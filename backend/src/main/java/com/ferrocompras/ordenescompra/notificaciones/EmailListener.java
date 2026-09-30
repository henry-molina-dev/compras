package com.ferrocompras.ordenescompra.notificaciones;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// AFTER_COMMIT + @Async: el correo solo se dispara si la transaccion que aprobo/anulo la orden
// realmente confirmo, y nunca bloquea ni participa en ella.
@Component
public class EmailListener {

    private final EmailService emailService;

    public EmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrdenAprobada(OrdenAprobadaEvent evento) {
        emailService.enviarNotificacionAprobacion(evento);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrdenAnulada(OrdenAnuladaEvent evento) {
        emailService.enviarNotificacionAnulacion(evento);
    }
}
