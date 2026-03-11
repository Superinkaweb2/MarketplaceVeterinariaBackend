package com.vet_saas.modules.delivery.scheduler;

import com.vet_saas.modules.delivery.model.Delivery;
import com.vet_saas.modules.delivery.model.DeliveryStatus;
import com.vet_saas.modules.delivery.repository.DeliveryRepository;
import com.vet_saas.modules.delivery.service.AsignacionService;
import com.vet_saas.modules.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeliveryScheduler {

    private final DeliveryRepository deliveryRepository;
    private final AsignacionService asignacionService;
    private final DeliveryService deliveryService;

    /** Máximo de reintentos antes de marcar como FALLIDO */
    private static final int MAX_INTENTOS = 5;

    /** Tiempo máximo sin repartidor antes de reintentar (en minutos) */
    private static final int TIMEOUT_BUSQUEDA_MIN = 2;

    /**
     * Cada 30 segundos: reintenta asignar repartidor a deliveries en espera.
     * Si supera MAX_INTENTOS, marca como FALLIDO y notifica.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void reintentarAsignaciones() {
        Instant hace = Instant.now().minus(TIMEOUT_BUSQUEDA_MIN, ChronoUnit.MINUTES);
        List<Delivery> pendientes = deliveryRepository.findBusquedaExpirada(hace);

        if (pendientes.isEmpty()) return;

        log.info("Scheduler: {} deliveries sin repartidor, reintentando...", pendientes.size());

        for (Delivery delivery : pendientes) {
            if (delivery.getIntentosAsignacion() >= MAX_INTENTOS) {
                log.warn("Delivery {} marcado como FALLIDO tras {} intentos",
                    delivery.getId(), MAX_INTENTOS);
                deliveryService.cambiarEstado(
                    delivery.getId(),
                    DeliveryStatus.FALLIDO,
                    null,
                    "Sin repartidores disponibles tras " + MAX_INTENTOS + " intentos"
                );
            } else {
                boolean asignado = asignacionService.intentarAsignar(delivery);
                log.info("Delivery {}: intento {} → {}",
                    delivery.getId(),
                    delivery.getIntentosAsignacion(),
                    asignado ? "ASIGNADO" : "sin candidatos");
            }
        }
    }
}
