package com.vet_saas.modules.payment.service;

import com.vet_saas.modules.payment.model.WebhookEvent;
import com.vet_saas.modules.payment.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookEventService {

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookOrchestrator webhookOrchestrator;

    @Transactional
    public WebhookEvent saveEvent(String paymentId, String empresaId) {
        WebhookEvent event = WebhookEvent.builder()
                .paymentId(paymentId)
                .empresaId(empresaId)
                .status("PENDING")
                .attempts(0)
                .maxAttempts(5)
                .createdAt(LocalDateTime.now())
                .nextRetryAt(LocalDateTime.now())
                .build();
        return webhookEventRepository.save(event);
    }

    @Transactional
    public void markCompleted(String paymentId) {
        webhookEventRepository.markCompletedByPaymentId(paymentId);
    }

    @Transactional
    public void markFailed(Long eventId, String error) {
        int nextDelayMinutes = calculateBackoff(eventId);
        webhookEventRepository.markFailed(eventId, error, LocalDateTime.now().plusMinutes(nextDelayMinutes));
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void retryFailedWebhooks() {
        var retryable = webhookEventRepository.findRetryableEvents(LocalDateTime.now());
        if (retryable.isEmpty()) return;

        log.info("Retrying {} failed webhook events", retryable.size());
        for (WebhookEvent event : retryable) {
            try {
                webhookOrchestrator.processWebhookAsync(event.getPaymentId(), event.getEmpresaId());
                markCompleted(event.getPaymentId());
            } catch (Exception e) {
                log.error("Retry failed for webhook event {}: {}", event.getId(), e.getMessage());
                markFailed(event.getId(), e.getMessage());
            }
        }
    }

    private int calculateBackoff(Long eventId) {
        return webhookEventRepository.findById(eventId)
                .map(e -> Math.min(60, (int) Math.pow(2, e.getAttempts()) * 5))
                .orElse(5);
    }
}
