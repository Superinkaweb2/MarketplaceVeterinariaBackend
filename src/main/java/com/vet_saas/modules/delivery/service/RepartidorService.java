package com.vet_saas.modules.delivery.service;

import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.core.exceptions.types.ResourceNotFoundException;
import com.vet_saas.modules.delivery.dto.request.UbicacionDTO;
import com.vet_saas.modules.delivery.dto.response.DeliveryResponseDTO;
import com.vet_saas.modules.delivery.dto.response.RepartidorResponseDTO;
import com.vet_saas.modules.delivery.mapper.DeliveryMapper;
import com.vet_saas.modules.delivery.model.DeliveryStatus;
import com.vet_saas.modules.delivery.model.Repartidor;
import com.vet_saas.modules.delivery.model.RepartidorStatus;
import com.vet_saas.modules.delivery.repository.DeliveryRepository;
import com.vet_saas.modules.delivery.repository.RepartidorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class RepartidorService {

    private final RepartidorRepository repartidorRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;

    @Transactional(readOnly = true)
    public RepartidorResponseDTO getByUsuarioId(Long usuarioId) {
        Repartidor r = repartidorRepository.findByUsuarioId(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Perfil de repartidor no encontrado"));
        return toDTO(r);
    }

    /** El repartidor activa/desactiva su disponibilidad */
    public void cambiarDisponibilidad(Long usuarioId, boolean disponible) {
        Repartidor repartidor = repartidorRepository.findByUsuarioId(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Repartidor no encontrado"));

        if (disponible && repartidor.getEstadoActual() == RepartidorStatus.OCUPADO) {
            throw new BusinessException("No puedes activarte mientras tienes un delivery activo");
        }

        RepartidorStatus nuevoEstado = disponible ? RepartidorStatus.DISPONIBLE : RepartidorStatus.OFFLINE;
        repartidorRepository.actualizarEstado(repartidor.getIdRepartidor(), nuevoEstado);
        log.info("Repartidor {} cambió a {}", repartidor.getIdRepartidor(), nuevoEstado);
    }

    /** Actualiza ubicacion GPS del repartidor */
    public void actualizarUbicacion(Long usuarioId, UbicacionDTO dto) {
        Repartidor repartidor = repartidorRepository.findByUsuarioId(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Repartidor no encontrado"));

        repartidorRepository.actualizarUbicacion(
            repartidor.getIdRepartidor(),
            dto.getLat(),
            dto.getLng()
        );
    }

    /** Delivery activo del repartidor (si tiene uno en curso) */
    @Transactional(readOnly = true)
    public DeliveryResponseDTO getDeliveryActivo(Long usuarioId) {
        Repartidor repartidor = repartidorRepository.findByUsuarioId(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Repartidor no encontrado"));

        return deliveryRepository.findByRepartidorIdRepartidorAndEstadoNotIn(
            repartidor.getIdRepartidor(),
            List.of(DeliveryStatus.ENTREGADO, DeliveryStatus.CANCELADO, DeliveryStatus.FALLIDO)
        ).map(deliveryMapper::toResponseDTO).orElse(null);
    }

    /** Historial de entregas del repartidor */
    @Transactional(readOnly = true)
    public List<DeliveryResponseDTO> getHistorial(Long usuarioId) {
        Repartidor repartidor = repartidorRepository.findByUsuarioId(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Repartidor no encontrado"));

        return deliveryRepository
            .findByRepartidorIdRepartidorOrderByCreatedAtDesc(repartidor.getIdRepartidor())
            .stream()
            .map(deliveryMapper::toResponseDTO)
            .toList();
    }

    private RepartidorResponseDTO toDTO(Repartidor r) {
        return RepartidorResponseDTO.builder()
            .idRepartidor(r.getIdRepartidor())
            .nombres(r.getNombres())
            .apellidos(r.getApellidos())
            .telefono(r.getTelefono())
            .fotoPerfil(r.getFotoPerfil())
            .tipoVehiculo(r.getTipoVehiculo())
            .placaVehiculo(r.getPlacaVehiculo())
            .estadoActual(r.getEstadoActual())
            .estadoValidacion(r.getEstadoValidacion())
            .calificacionPromedio(r.getCalificacionPromedio())
            .totalEntregas(r.getTotalEntregas())
            .ubicacionLat(r.getUbicacionLat())
            .ubicacionLng(r.getUbicacionLng())
            .ultimaUbicacionAt(r.getUltimaUbicacionAt())
            .activo(r.getActivo())
            .build();
    }
}
