package com.vet_saas.modules.payment.controller;

import com.vet_saas.modules.payment.dto.PaymentPreferenceResponse;
import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.payment.service.PaymentService;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final Logger LOGGER = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    @PostMapping("/checkout/{orderId}")
    public ResponseEntity<ApiResponse<PaymentPreferenceResponse>> generatePaymentLink(
            @PathVariable Long orderId,
            @AuthenticationPrincipal Usuario usuarioActual) {

        PaymentPreferenceResponse response = paymentService.createCheckoutUrl(orderId, usuarioActual);

        LOGGER.info("Checkout link generated orderId={}", orderId);
        return ResponseEntity.ok(ApiResponse.success(response, "Link de pago generado exitosamente"));
    }

    @PostMapping("/webhook/{empresaId}")
    public ResponseEntity<Void> receiveWebhook(
            @PathVariable String empresaId,
            @RequestParam Map<String, String> queryParams,
            @RequestBody(required = false) Map<String, Object> body) {
        return handleWebhook(empresaId, queryParams, body);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receivePlatformWebhook(
            @RequestParam Map<String, String> queryParams,
            @RequestBody(required = false) Map<String, Object> body) {
        // Para suscripciones, la empresa_id vendrá en la metadata, no en el path
        return handleWebhook(null, queryParams, body);
    }

    private ResponseEntity<Void> handleWebhook(
            String pathEmpresaId,
            Map<String, String> queryParams,
            Map<String, Object> body) {

        String type = queryParams.get("type");
        String topic = queryParams.get("topic");

        String id = queryParams.get("data.id");
        if (id == null)
            id = queryParams.get("id");
        if (id == null && body != null && body.containsKey("data")) {
            Object dataObj = body.get("data");
            if (dataObj instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) dataObj;
                if (data.containsKey("id")) {
                    id = data.get("id").toString();
                }
            }
        }

        if (id == null) {
            LOGGER.warn("Webhook sin ID. Query: {}, Body: {}", queryParams, body);
            return ResponseEntity.ok().build();
        }

        if ("payment".equals(type) || "payment".equals(topic)) {
            String finalId = id;
            CompletableFuture.runAsync(() -> {
                try {
                    paymentService.processWebhook(finalId, pathEmpresaId);
                } catch (DataIntegrityViolationException | UnexpectedRollbackException e) {
                    // Concurrent duplicate webhook — safely ignored (idempotency)
                    LOGGER.info("Webhook duplicado ignorado por concurrencia. paymentId: {}", finalId);
                } catch (Exception e) {
                    LOGGER.error("Error en procesamiento asíncrono de webhook", e);
                }
            });
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/sync")
    public ResponseEntity<ApiResponse<Void>> syncPayment(
            @RequestParam("payment_id") String paymentId,
            @RequestParam("external_reference") String codigoOrden) {
        paymentService.syncPaymentStatus(paymentId, codigoOrden);
        return ResponseEntity.ok(ApiResponse.success(null, "Pago sincronizado correctamente"));
    }
}