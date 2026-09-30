package com.ferrocompras.ordenescompra.notificaciones;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void enviarNotificacionAprobacion(OrdenAprobadaEvent evento) {
        enviar(evento.proveedorEmail(), "Orden de compra %s aprobada".formatted(evento.numeroOrden()),
            cuerpoAprobacion(evento), evento.numeroOrden(), evento.pdfOrden());
    }

    public void enviarNotificacionAnulacion(OrdenAnuladaEvent evento) {
        enviar(evento.proveedorEmail(), "Orden de compra %s anulada".formatted(evento.numeroOrden()),
            cuerpoAnulacion(evento), evento.numeroOrden(), evento.pdfOrden());
    }

    // El adjunto es "mejor esfuerzo": si el PDF no se pudo renderizar (pdfOrden == null, ver
    // OrdenCompraService), el correo se envia igual, solo que sin el archivo - la notificacion en
    // si no debe perderse por un problema de renderizado.
    private void enviar(String destinatario, String asunto, String cuerpo, String numeroOrden, byte[] pdfOrden) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, pdfOrden != null);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpo);
            if (pdfOrden != null) {
                helper.addAttachment(numeroOrden + ".pdf", new ByteArrayResource(pdfOrden));
            }
            mailSender.send(mensaje);
        } catch (MailException | MessagingException ex) {
            // El envio es asincrono y no transaccional respecto al cambio de estado: si falla, se
            // registra el error pero la transicion ya confirmada no se revierte.
            log.error("No se pudo enviar el correo a {}: {}", destinatario, ex.getMessage(), ex);
        }
    }

    private String cuerpoAprobacion(OrdenAprobadaEvent evento) {
        StringBuilder detalle = new StringBuilder();
        for (OrdenAprobadaEvent.Linea linea : evento.detalle()) {
            detalle.append(" - ").append(linea.productoNombre()).append(": ")
                .append(linea.cantidad()).append(' ').append(linea.unidadCompra()).append('\n');
        }

        return """
            Estimado %s,

            Le confirmamos que la orden de compra %s ha sido aprobada.

            Fecha de necesidad: %s

            Detalle:
            %s
            Total: $%s
            """.formatted(evento.proveedorNombre(), evento.numeroOrden(), evento.fechaNecesaria(),
                detalle, evento.total());
    }

    private String cuerpoAnulacion(OrdenAnuladaEvent evento) {
        return """
            Estimado %s,

            Le informamos que la orden de compra %s ha sido anulada.

            Motivo: %s
            Fecha de anulacion: %s
            """.formatted(evento.proveedorNombre(), evento.numeroOrden(),
                evento.motivoAnulacion(), evento.fechaAnulacion());
    }
}
