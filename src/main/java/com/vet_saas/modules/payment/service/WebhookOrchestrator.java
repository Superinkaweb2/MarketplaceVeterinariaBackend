package com.vet_saas.modules.payment.service;

import com.mercadopago.resources.payment.Payment;
import com.vet_saas.config.AppProperties;
import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.core.utils.CryptoUtil;
import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.company.repository.EmpresaRepository;
import com.vet_saas.modules.payment.gateway.MercadoPagoGateway;
import com.vet_saas.modules.veterinarian.model.Veterinario;
import com.vet_saas.modules.veterinarian.repository.VeterinarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebhookOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookOrchestrator.class);

    private final MercadoPagoGateway mpGateway;
    private final PaymentService paymentService;
    private final AppProperties appProperties;
    private final CryptoUtil cryptoUtil;
    private final EmpresaRepository empresaRepository;
    private final VeterinarioRepository veterinarioRepository;

    @Async("webhookExecutor")
    public void processWebhookAsync(String paymentId, String pathEmpresaId) {
        LOGGER.info("Iniciando procesamiento asíncrono de webhook. paymentId: {}", paymentId);

        try {
            String tokenToUse = determineTokenToUse(pathEmpresaId);

            Payment payment = mpGateway.getPaymentDetails(paymentId, tokenToUse);
            Map<String, Object> metadata = payment.getMetadata();

            if (metadata == null) {
                LOGGER.error("El pago {} no contiene metadata", paymentId);
                return;
            }

            paymentService.processPaymentDatabaseTransaction(payment, metadata, pathEmpresaId);

        } catch (Exception ex) {
            LOGGER.error("Error crítico al procesar el pago asíncrono {}", paymentId, ex);
        }
    }

    private String determineTokenToUse(String pathEmpresaId) {
        if (pathEmpresaId == null) {
            return appProperties.getExternal().getMercadoPago().getAccessToken();
        }

        if (pathEmpresaId.startsWith("vet_")) {
            Long vetId = Long.parseLong(pathEmpresaId.substring(4));
            Veterinario veterinario = veterinarioRepository.findById(vetId)
                    .orElseThrow(() -> new BusinessException("Veterinario " + vetId + " no encontrado"));
            return cryptoUtil.decrypt(veterinario.getMpAccessToken());
        }

        Long empId = Long.parseLong(pathEmpresaId);
        Empresa empresa = empresaRepository.findById(empId)
                .orElseThrow(() -> new BusinessException("Empresa " + empId + " no encontrada"));
        return cryptoUtil.decrypt(empresa.getMpAccessToken());
    }
}