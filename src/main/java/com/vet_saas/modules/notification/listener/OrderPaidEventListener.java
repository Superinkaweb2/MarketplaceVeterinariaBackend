package com.vet_saas.modules.notification.listener;

import com.vet_saas.modules.notification.service.EmailService;
import com.vet_saas.modules.sales.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaidEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderPaidEventListener.class);
    private final EmailService emailService;

    @EventListener
    public void handleOrderPaidEvent(OrderPaidEvent event) {
        LOGGER.info("Evento OrderPaid detectado. Preparando envío de recibo para la orden {}",
                event.getOrden().getCodigoOrden());

        emailService.sendOrderConfirmation(event.getOrden().getId());
    }
}
