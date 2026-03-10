package com.vet_saas.modules.delivery.controller;

import com.vet_saas.modules.delivery.dto.request.UbicacionDTO;
import com.vet_saas.modules.delivery.dto.response.DeliveryResponseDTO;
import com.vet_saas.modules.delivery.dto.response.RepartidorResponseDTO;
import com.vet_saas.modules.delivery.service.RepartidorService;
import com.vet_saas.modules.user.model.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/repartidores")
@RequiredArgsConstructor
public class RepartidorController {

    private final RepartidorService repartidorService;

    /** Perfil del repartidor autenticado */
    @GetMapping("/me")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<RepartidorResponseDTO> getMiPerfil(
            @AuthenticationPrincipal Usuario principal) {
        return ResponseEntity.ok(repartidorService.getByUsuarioId(principal.getId()));
    }

    /** El repartidor activa o desactiva su disponibilidad */
    @PatchMapping("/me/disponibilidad")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<Void> cambiarDisponibilidad(
            @RequestParam boolean disponible,
            @AuthenticationPrincipal Usuario principal) {
        repartidorService.cambiarDisponibilidad(principal.getId(), disponible);
        return ResponseEntity.ok().build();
    }

    /**
     * Actualizar ubicacion GPS via HTTP (complementa el WebSocket).
     * Usa esto como fallback si el WS se desconecta.
     */
    @PatchMapping("/me/ubicacion")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<Void> actualizarUbicacion(
            @Valid @RequestBody UbicacionDTO dto,
            @AuthenticationPrincipal Usuario principal) {
        repartidorService.actualizarUbicacion(principal.getId(), dto);
        return ResponseEntity.ok().build();
    }

    /** Delivery activo actual del repartidor */
    @GetMapping("/me/delivery-activo")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<DeliveryResponseDTO> getDeliveryActivo(
            @AuthenticationPrincipal Usuario principal) {
        DeliveryResponseDTO activo = repartidorService.getDeliveryActivo(principal.getId());
        return activo != null ? ResponseEntity.ok(activo) : ResponseEntity.noContent().build();
    }

    /** Historial de entregas del repartidor */
    @GetMapping("/me/historial")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<List<DeliveryResponseDTO>> getHistorial(
            @AuthenticationPrincipal Usuario principal) {
        return ResponseEntity.ok(repartidorService.getHistorial(principal.getId()));
    }
}
