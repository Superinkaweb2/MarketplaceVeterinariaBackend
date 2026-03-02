package com.vet_saas.modules.payment.controller;

import com.vet_saas.modules.payment.dto.PaymentPreferenceResponse;
import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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
            @PathVariable Long orderId) {

        PaymentPreferenceResponse response = paymentService.createCheckoutUrl(orderId);

        LOGGER.info("Checkout link generated orderId={}", orderId);

        return ResponseEntity.ok(
                ApiResponse.success(response, "Link de pago generado exitosamente"));
    }

    @PostMapping("/webhook/{empresaId}")
    public ResponseEntity<Void> receiveWebhook(
            @PathVariable Long empresaId,
            @RequestParam Map<String, String> queryParams,
            @RequestBody(required = false) Map<String, Object> body) {
        String type = queryParams.get("type");
        String topic = queryParams.get("topic");

        String id = queryParams.get("data.id");
        if (id == null)
            id = queryParams.get("id");
        if (id == null && body != null && body.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            id = data.get("id").toString();
        }

        if (id == null) {
            LOGGER.warn("Webhook sin ID. Query: {}, Body: {}", queryParams, body);
            return ResponseEntity.ok().build();
        }

        if ("payment".equals(type) || "payment".equals(topic)) {
            String finalId = id;
            CompletableFuture.runAsync(() -> {
                try {
                    paymentService.processWebhook(finalId, empresaId);
                } catch (Exception e) {
                    LOGGER.error("Error en procesamiento asíncrono", e);
                }
            });
        }

        return ResponseEntity.ok().build();
    }
}