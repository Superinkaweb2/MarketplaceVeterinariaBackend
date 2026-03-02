package com.vet_saas.modules.notification.listener;

import com.vet_saas.modules.notification.service.EmailService;
import com.vet_saas.modules.sales.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final EmailService emailService;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPaid(OrderPaidEvent event) {
        System.out.println("📩 Evento recibido: Enviando email para orden " + event.getOrden().getCodigoOrden());
        emailService.sendOrderConfirmation(event.getOrden().getId());
    }
}