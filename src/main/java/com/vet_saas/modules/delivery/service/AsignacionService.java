package com.vet_saas.modules.delivery.service;

import com.vet_saas.modules.delivery.model.Delivery;
import com.vet_saas.modules.delivery.model.DeliveryStatus;
import com.vet_saas.modules.delivery.model.Repartidor;
import com.vet_saas.modules.delivery.model.RepartidorStatus;
import com.vet_saas.modules.delivery.repository.DeliveryRepository;
import com.vet_saas.modules.delivery.mapper.DeliveryMapper;
import com.vet_saas.modules.delivery.repository.RepartidorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsignacionService {

    private final RepartidorRepository repartidorRepository;
    private final DeliveryRepository deliveryRepository;
    private final SimpMessagingTemplate wsTemplate;
    private final DeliveryMapper deliveryMapper;

    private static final double RADIO_INICIAL_KM = 3.0;
    private static final double RADIO_EXPANSION_KM = 2.0;
    private static final double RADIO_MAXIMO_KM = 15.0;
    private static final int LIMITE_CANDIDATOS = 5;

    /**
     * Intenta asignar un repartidor disponible al delivery.
     * Si no hay repartidores, incrementa intentos (el scheduler reintentará).
     */
    @Transactional
    public boolean intentarAsignar(Delivery delivery) {
        // Radio dinámico: crece con cada intento fallido
        double radio = Math.min(
            RADIO_INICIAL_KM + (delivery.getIntentosAsignacion() * RADIO_EXPANSION_KM),
            RADIO_MAXIMO_KM
        );

        List<Repartidor> candidatos = repartidorRepository.findDisponiblesEnRadio(
            delivery.getOrigenLat().doubleValue(),
            delivery.getOrigenLng().doubleValue(),
            radio,
            LIMITE_CANDIDATOS
        );

        if (candidatos.isEmpty()) {
            log.info("Delivery {}: sin repartidores en {}km. Intento {}",
                delivery.getId(), radio, delivery.getIntentosAsignacion() + 1);
            delivery.setIntentosAsignacion(delivery.getIntentosAsignacion() + 1);
            deliveryRepository.save(delivery);
            return false;
        }

        // Tomamos el más cercano (ya viene ordenado por Haversine)
        Repartidor elegido = candidatos.get(0);
        asignar(delivery, elegido);
        return true;
    }

    @Transactional
    public void asignar(Delivery delivery, Repartidor repartidor) {
        // Marcar repartidor como OCUPADO
        repartidor.setEstadoActual(RepartidorStatus.OCUPADO);
        repartidorRepository.save(repartidor);

        // Actualizar delivery
        delivery.setRepartidor(repartidor);
        delivery.setEstado(DeliveryStatus.REPARTIDOR_ASIGNADO);
        delivery.setAsignadoAt(Instant.now());
        deliveryRepository.save(delivery);

        log.info("Delivery {} asignado al repartidor {}",
            delivery.getId(), repartidor.getIdRepartidor());

        // Notificar al repartidor via WebSocket
        wsTemplate.convertAndSendToUser(
            repartidor.getUsuario().getId().toString(),
            "/queue/pedidos",
            deliveryMapper.toResponseDTO(delivery)
        );
    }
}
