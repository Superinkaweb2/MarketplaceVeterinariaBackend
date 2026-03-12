package com.vet_saas.modules.notification.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.vet_saas.config.AppProperties;
import com.vet_saas.modules.sales.model.Orden;
import com.vet_saas.modules.sales.repository.OrdenRepository;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private final TemplateEngine templateEngine;
    private final OrdenRepository ordenRepository;
    private final AppProperties appProperties;

    private Resend getResendClient() {
        String apiKey = appProperties.getExternal().getResend().getApiKey();
        LOGGER.info("Initializing Resend client with API Key (length: {})", apiKey != null ? apiKey.length() : "null");
        return new Resend(apiKey);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            Resend resend = getResendClient();
            String from = appProperties.getExternal().getResend().getFromEmail();

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .html(htmlContent)
                    .build();

            CreateEmailResponse response = resend.emails().send(params);
            LOGGER.info("Email sent successfully via Resend. ID: {}", response.getId());
        } catch (ResendException ex) {
            LOGGER.error("Resend API error sending email to {}: {}", to, ex.getMessage(), ex);
        } catch (Exception ex) {
            LOGGER.error("Unexpected error sending email to {}: {}", to, ex.getMessage(), ex);
        }
    }

    @Async("mailExecutor")
    @Transactional(readOnly = true)
    public void sendOrderConfirmation(Long ordenId) {
        try {
            Orden orden = ordenRepository.findByIdForEmail(ordenId)
                    .orElseThrow(() -> new IllegalStateException("Order not found for email: " + ordenId));

            String emailDestino = orden.getUsuarioCliente().getCorreo();
            if (emailDestino == null || emailDestino.isBlank()) return;

            Context context = new Context();
            context.setVariable("nombreCliente", emailDestino);
            context.setVariable("codigoOrden", orden.getCodigoOrden());
            context.setVariable("nombreEmpresa", orden.getEmpresa().getNombreComercial());
            context.setVariable("items", orden.getDetalles());
            context.setVariable("total", orden.getTotal());

            String htmlContent = templateEngine.process("email/order-confirmation", context);
            sendEmail(emailDestino, "Confirmación de compra - " + orden.getCodigoOrden(), htmlContent);

        } catch (Exception ex) {
            LOGGER.error("Error preparing order confirmation email orderId={}: {}", ordenId, ex.getMessage(), ex);
        }
    }

    @Async("mailExecutor")
    @Transactional(readOnly = true)
    public void sendDeliveryOtpEmail(Long ordenId, String otpCode) {
        try {
            Orden orden = ordenRepository.findByIdForEmail(ordenId)
                    .orElseThrow(() -> new IllegalStateException("Order not found for OTP email: " + ordenId));

            String emailDestino = orden.getUsuarioCliente().getCorreo();
            if (emailDestino == null || emailDestino.isBlank()) return;

            Context context = new Context();
            context.setVariable("nombreCliente", emailDestino);
            context.setVariable("codigoOrden", orden.getCodigoOrden());
            context.setVariable("nombreEmpresa", orden.getEmpresa().getNombreComercial());
            context.setVariable("items", orden.getDetalles());
            context.setVariable("total", orden.getTotal());
            context.setVariable("otpCode", otpCode);
            context.setVariable("costoEnvio", orden.getCostoEnvio() != null ? orden.getCostoEnvio() : BigDecimal.ZERO);

            String htmlContent = templateEngine.process("email/order-confirmation", context); // Usando el mismo template por ahora
            sendEmail(emailDestino, "Tu código de entrega - " + orden.getCodigoOrden(), htmlContent);

        } catch (Exception ex) {
            LOGGER.error("Error preparing delivery OTP email orderId={}: {}", ordenId, ex.getMessage(), ex);
        }
    }

    @Async("mailExecutor")
    public void sendWelcomeEmail(Usuario usuario) {
        try {
            if (usuario.getCorreo() == null || usuario.getCorreo().isBlank()) return;

            Context context = new Context();
            context.setVariable("nombreUsuario", usuario.getCorreo());

            String htmlContent = templateEngine.process("email/welcome-email", context);
            sendEmail(usuario.getCorreo(), "¡Bienvenido a VetSaaS!", htmlContent);

        } catch (Exception ex) {
            LOGGER.error("Error preparing welcome email to {}: {}", usuario.getCorreo(), ex.getMessage(), ex);
        }
    }

    @Async("mailExecutor")
    public void sendVerificationEmail(Usuario usuario, String token) {
        try {
            String frontendUrl = appProperties.getExternal().getFrontendUrl();
            Context context = new Context();
            context.setVariable("nombreUsuario", usuario.getCorreo());
            context.setVariable("token", token);
            String verificationUrl = frontendUrl + "/auth/verify-email?token=" + token;
            context.setVariable("verificationUrl", verificationUrl);

            String htmlContent = templateEngine.process("email/verify-email", context);
            sendEmail(usuario.getCorreo(), "Verifica tu correo - VetSaaS", htmlContent);

        } catch (Exception ex) {
            LOGGER.error("Error preparing verification email to {}: {}", usuario.getCorreo(), ex.getMessage(), ex);
        }
    }

    @Async("mailExecutor")
    public void sendPasswordResetEmail(Usuario usuario, String token) {
        try {
            String frontendUrl = appProperties.getExternal().getFrontendUrl();
            Context context = new Context();
            context.setVariable("nombreUsuario", usuario.getCorreo());
            context.setVariable("token", token);
            String resetUrl = frontendUrl + "/auth/reset-password?token=" + token;
            context.setVariable("resetUrl", resetUrl);

            String htmlContent = templateEngine.process("email/password-reset", context);
            sendEmail(usuario.getCorreo(), "Restablece tu contraseña - VetSaaS", htmlContent);

        } catch (Exception ex) {
            LOGGER.error("Error preparing reset email to {}: {}", usuario.getCorreo(), ex.getMessage(), ex);
        }
    }
}
