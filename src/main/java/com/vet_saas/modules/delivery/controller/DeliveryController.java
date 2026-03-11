package com.vet_saas.modules.delivery.controller;

import com.vet_saas.modules.delivery.dto.request.CalificacionDTO;
import com.vet_saas.modules.delivery.dto.request.CambiarEstadoDTO;
import com.vet_saas.modules.delivery.dto.request.ConfirmarOTPDTO;
import com.vet_saas.modules.delivery.dto.response.DeliveryResponseDTO;
import com.vet_saas.modules.delivery.service.DeliveryService;
import com.vet_saas.modules.user.model.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    // ---- Cliente ----

    /** Consultar el delivery de una orden (cliente ve estado + posicion del repartidor) */
    @GetMapping("/orden/{ordenId}")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<DeliveryResponseDTO> getByOrden(
            @PathVariable Long ordenId) {
        return ResponseEntity.ok(deliveryService.getByOrdenId(ordenId));
    }

    /** El cliente califica al repartidor tras recibir su pedido */
    @PostMapping("/{deliveryId}/calificar")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<Void> calificar(
            @PathVariable Long deliveryId,
            @Valid @RequestBody CalificacionDTO dto,
            @AuthenticationPrincipal Usuario principal) {
        deliveryService.calificarEntrega(deliveryId, dto, principal.getId());
        return ResponseEntity.ok().build();
    }

    /** El cliente cancela su pedido (solo si no ha sido recogido) */
    @PostMapping("/{deliveryId}/cancelar")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<DeliveryResponseDTO> cancelar(
            @PathVariable Long deliveryId,
            @AuthenticationPrincipal Usuario principal) {
        return ResponseEntity.ok(deliveryService.cancelarDelivery(deliveryId, principal.getId()));
    }

    // ---- Repartidor ----

    /** El repartidor cambia el estado del delivery (EN_TIENDA, RECOGIDO, EN_CAMINO, etc.) */
    @PatchMapping("/{deliveryId}/estado")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<DeliveryResponseDTO> cambiarEstado(
            @PathVariable Long deliveryId,
            @Valid @RequestBody CambiarEstadoDTO dto,
            @AuthenticationPrincipal Usuario principal) {
        return ResponseEntity.ok(
            deliveryService.cambiarEstado(deliveryId, dto.getNuevoEstado(),
                principal.getId(), dto.getDescripcion())
        );
    }

    /** Confirmar entrega con OTP que el cliente le dice al repartidor */
    @PostMapping("/{deliveryId}/confirmar-otp")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<Void> confirmarOTP(
            @PathVariable Long deliveryId,
            @Valid @RequestBody ConfirmarOTPDTO dto) {
        deliveryService.confirmarEntregaOTP(deliveryId, dto);
        return ResponseEntity.ok().build();
    }

    /** Confirmar entrega con foto (cuando nadie abre o como evidencia adicional) */
    @PostMapping("/{deliveryId}/confirmar-foto")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<Void> confirmarFoto(
            @PathVariable Long deliveryId,
            @RequestParam("foto") MultipartFile foto,
            @AuthenticationPrincipal Usuario principal) {
        deliveryService.confirmarEntregaFoto(deliveryId, foto, principal.getId());
        return ResponseEntity.ok().build();
    }

    /** Reportar intento fallido (nadie abre la puerta) */
    @PostMapping("/{deliveryId}/intento-fallido")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<Void> intentoFallido(
            @PathVariable Long deliveryId,
            @RequestParam(value = "foto", required = false) MultipartFile foto,
            @RequestParam(value = "motivo", required = false) String motivo,
            @AuthenticationPrincipal Usuario principal) {
        deliveryService.reportarIntentoFallido(deliveryId, foto, motivo, principal.getId());
        return ResponseEntity.ok().build();
    }

    /** Repartidor ve el pool de pedidos disponibles */
    @GetMapping("/disponibles")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<java.util.List<DeliveryResponseDTO>> getDisponibles() {
        return ResponseEntity.ok(deliveryService.getPedidosDisponibles());
    }

    /** Repartidor toma un pedido del pool */
    @PostMapping("/{deliveryId}/aceptar")
    @PreAuthorize("hasRole('REPARTIDOR')")
    public ResponseEntity<DeliveryResponseDTO> aceptarPedido(
            @PathVariable Long deliveryId,
            @AuthenticationPrincipal Usuario principal) {
        return ResponseEntity.ok(deliveryService.aceptarPedido(deliveryId, principal.getId()));
    }

    // ---- Empresa (dashboard) ----

    /** Ver detalle de un delivery específico */
    @GetMapping("/{deliveryId}")
    @PreAuthorize("hasAnyRole('EMPRESA', 'ADMIN', 'REPARTIDOR')")
    public ResponseEntity<DeliveryResponseDTO> getById(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(deliveryService.getById(deliveryId));
    }

    /** Ver calificaciones recibidas por la empresa */
    @GetMapping("/empresa/ratings")
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<java.util.List<DeliveryResponseDTO>> getRatingsByEmpresa(
            @AuthenticationPrincipal Usuario principal) {
        return ResponseEntity.ok(deliveryService.getRatingsByUsuarioEmpresa(principal.getId()));
    }
}
