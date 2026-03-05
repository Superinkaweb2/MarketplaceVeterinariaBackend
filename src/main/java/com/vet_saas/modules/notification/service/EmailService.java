package com.vet_saas.modules.notification.service;

import com.vet_saas.config.AppProperties;
import com.vet_saas.modules.sales.model.Orden;
import com.vet_saas.modules.sales.repository.OrdenRepository;
import com.vet_saas.modules.user.model.Usuario;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final OrdenRepository ordenRepository;
    private final AppProperties appProperties;

    /**
     * Envia el email de confirmacion de orden.
     * Se ejecuta de forma asincrona y no debe afectar el flujo principal del
     * dominio.
     */
    @Async("mailExecutor")
    @Transactional(readOnly = true)
    public void sendOrderConfirmation(Long ordenId) {

        try {
            Orden orden = ordenRepository.findByIdForEmail(ordenId)
                    .orElseThrow(() -> new IllegalStateException("Order not found for email: " + ordenId));

            String emailDestino = orden.getUsuarioCliente().getCorreo();

            if (emailDestino == null || emailDestino.isBlank()) {
                LOGGER.warn("Email not sent. Missing customer email orderId={}", ordenId);
                return;
            }

            Context context = new Context();
            context.setVariable("nombreCliente", emailDestino);
            context.setVariable("codigoOrden", orden.getCodigoOrden());
            context.setVariable("nombreEmpresa", orden.getEmpresa().getNombreComercial());
            context.setVariable("items", orden.getDetalles());
            context.setVariable("total", orden.getTotal());

            String htmlContent = templateEngine.process("email/order-confirmation", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(emailDestino);
            helper.setSubject("Confirmación de compra - " + orden.getCodigoOrden());
            helper.setText(htmlContent, true);
            helper.setFrom(appProperties.getExternal().getMail().getUsername());

            mailSender.send(message);

            LOGGER.info("Order confirmation email sent orderId={} email={}",
                    ordenId,
                    emailDestino);

        } catch (MessagingException ex) {

            LOGGER.error("Email sending failed orderId={} error={}",
                    ordenId,
                    ex.getMessage(),
                    ex);

        } catch (Exception ex) {

            LOGGER.error("Unexpected error sending email orderId={} error={}",
                    ordenId,
                    ex.getMessage(),
                    ex);
        }
    }

    /**
     * Envia un correo de bienvenida a nuevos usuarios.
     * Ejecuta de forma asíncrona.
     */
    @Async("mailExecutor")
    public void sendWelcomeEmail(Usuario usuario) {
        try {
            if (usuario.getCorreo() == null || usuario.getCorreo().isBlank()) {
                LOGGER.warn("Bienvenida no enviada. Usuario o correo vacío.");
                return;
            }

            Context context = new Context();
            context.setVariable("nombreUsuario", usuario.getCorreo());

            // Si tuvieramos nombre real, lo usariamos. Por ahora el correo.

            String htmlContent = templateEngine.process("email/welcome-email", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(usuario.getCorreo());
            helper.setSubject("¡Bienvenido a VetSaaS!");
            helper.setText(htmlContent, true);
            helper.setFrom(appProperties.getExternal().getMail().getUsername());

            mailSender.send(message);

            LOGGER.info("Welcome email sent to {}", usuario.getCorreo());

        } catch (Exception ex) {
            LOGGER.error("Error sending welcome email to {} error={}", usuario.getCorreo(), ex.getMessage(), ex);
        }
    }

    /**
     * Envia un correo de verificación de email.
     */
    @Async("mailExecutor")
    public void sendVerificationEmail(Usuario usuario, String token) {
        try {
            Context context = new Context();
            context.setVariable("nombreUsuario", usuario.getCorreo());
            context.setVariable("token", token);
            // URL base extraída de config o properties
            String verificationUrl = "http://localhost:5173/auth/verify-email?token=" + token;
            context.setVariable("verificationUrl", verificationUrl);

            String htmlContent = templateEngine.process("email/verify-email", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(usuario.getCorreo());
            helper.setSubject("Verifica tu correo - VetSaaS");
            helper.setText(htmlContent, true);
            helper.setFrom(appProperties.getExternal().getMail().getUsername());

            mailSender.send(message);

            LOGGER.info("Verification email sent to {}", usuario.getCorreo());
        } catch (Exception ex) {
            LOGGER.error("Error sending verification email to {} error={}", usuario.getCorreo(), ex.getMessage(), ex);
        }
    }

    /**
     * Envia un correo de restablecimiento de contraseña.
     */
    @Async("mailExecutor")
    public void sendPasswordResetEmail(Usuario usuario, String token) {
        try {
            Context context = new Context();
            context.setVariable("nombreUsuario", usuario.getCorreo());
            context.setVariable("token", token);
            String resetUrl = "http://localhost:5173/auth/reset-password?token=" + token;
            context.setVariable("resetUrl", resetUrl);

            String htmlContent = templateEngine.process("email/password-reset", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(usuario.getCorreo());
            helper.setSubject("Restablece tu contraseña - VetSaaS");
            helper.setText(htmlContent, true);
            helper.setFrom(appProperties.getExternal().getMail().getUsername());

            mailSender.send(message);

            LOGGER.info("Password reset email sent to {}", usuario.getCorreo());
        } catch (Exception ex) {
            LOGGER.error("Error sending reset email to {} error={}", usuario.getCorreo(), ex.getMessage(), ex);
        }
    }
}
