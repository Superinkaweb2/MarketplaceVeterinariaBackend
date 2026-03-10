package com.vet_saas.modules.delivery.service;

import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.core.exceptions.types.ResourceNotFoundException;
import com.vet_saas.core.service.StorageService;
import com.vet_saas.modules.delivery.dto.request.CalificacionDTO;
import com.vet_saas.modules.delivery.dto.request.ConfirmarOTPDTO;
import com.vet_saas.modules.delivery.dto.request.CrearDeliveryDTO;
import com.vet_saas.modules.delivery.dto.response.DeliveryResponseDTO;
import com.vet_saas.modules.delivery.dto.response.EstadoDeliveryEvent;
import com.vet_saas.modules.delivery.mapper.DeliveryMapper;
import com.vet_saas.modules.delivery.model.Delivery;
import com.vet_saas.modules.delivery.model.DeliveryEstado;
import com.vet_saas.modules.delivery.model.DeliveryStatus;
import com.vet_saas.modules.delivery.model.RepartidorStatus;
import com.vet_saas.modules.delivery.repository.DeliveryEstadoRepository;
import com.vet_saas.modules.delivery.repository.DeliveryRepository;
import com.vet_saas.modules.delivery.repository.RepartidorRepository;
import com.vet_saas.modules.sales.model.Orden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEstadoRepository estadoRepository;
    private final RepartidorRepository repartidorRepository;
    private final AsignacionService asignacionService;
    private final StorageService cloudinaryService;
    private final SimpMessagingTemplate wsTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DeliveryMapper deliveryMapper;

    private static final int OTP_EXPIRACION_HORAS = 4;
    private static final String CLOUDINARY_FOLDER_DELIVERY = "deliveries/confirmaciones";

    // =========================================================
    // CREAR DELIVERY (llamado desde OrdenService al crear orden)
    // =========================================================
    public DeliveryResponseDTO crearDelivery(Orden orden, CrearDeliveryDTO dto) {
        if (deliveryRepository.existsByOrdenId(orden.getId())) {
            throw new BusinessException("Ya existe un delivery para esta orden");
        }

        Delivery delivery = Delivery.builder()
            .orden(orden)
            .origenLat(dto.getOrigenLat())
            .origenLng(dto.getOrigenLng())
            .origenDireccion(dto.getOrigenDireccion())
            .destinoLat(dto.getDestinoLat())
            .destinoLng(dto.getDestinoLng())
            .destinoDireccion(dto.getDestinoDireccion())
            .destinoReferencia(dto.getDestinoReferencia())
            .costoDelivery(dto.getCostoDelivery())
            .estado(DeliveryStatus.BUSCANDO_REPARTIDOR)
            .intentosAsignacion(0)
            .build();

        delivery = deliveryRepository.save(delivery);
        registrarEstado(delivery, DeliveryStatus.BUSCANDO_REPARTIDOR, "Delivery creado", null);

        // Generar OTP y guardarlo hasheado
        String otpPlano = generarYGuardarOTP(delivery);

        // Intentar asignar repartidor inmediatamente
        asignacionService.intentarAsignar(delivery);

        // Recargar para tener datos actualizados
        delivery = deliveryRepository.findById(delivery.getIdDelivery()).orElseThrow();
        return deliveryMapper.toResponseDTOConOTP(delivery, otpPlano);
    }

    // =========================================================
    // CONSULTAR DELIVERY
    // =========================================================
    @Transactional(readOnly = true)
    public DeliveryResponseDTO getByOrdenId(Long ordenId) {
        Delivery delivery = deliveryRepository.findByOrdenId(ordenId)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery no encontrado para orden: " + ordenId));
        return deliveryMapper.toResponseDTO(delivery);
    }

    @Transactional(readOnly = true)
    public DeliveryResponseDTO getById(Long deliveryId) {
        return deliveryMapper.toResponseDTO(
            deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery no encontrado: " + deliveryId))
        );
    }

    // =========================================================
    // CAMBIAR ESTADO (repartidor lo llama desde su app)
    // =========================================================
    public DeliveryResponseDTO cambiarEstado(Long deliveryId, DeliveryStatus nuevoEstado,
                                              Long usuarioId, String descripcion) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery no encontrado: " + deliveryId));

        if (delivery.getEstado().esFinal()) {
            throw new BusinessException("El delivery ya está en estado final: " + delivery.getEstado());
        }

        if (!delivery.getEstado().puedeTransicionarA(nuevoEstado)) {
            throw new BusinessException(
                String.format("Transición inválida: %s → %s", delivery.getEstado(), nuevoEstado)
            );
        }

        delivery.setEstado(nuevoEstado);
        marcarTimestamp(delivery, nuevoEstado);

        // Si el delivery termina (entregado, fallido, cancelado), liberar repartidor
        if (nuevoEstado.esFinal() && delivery.getRepartidor() != null) {
            repartidorRepository.actualizarEstado(
                delivery.getRepartidor().getIdRepartidor(),
                RepartidorStatus.DISPONIBLE
            );
            if (nuevoEstado == DeliveryStatus.ENTREGADO) {
                // Incrementar contador de entregas del repartidor
                delivery.getRepartidor().setTotalEntregas(
                    delivery.getRepartidor().getTotalEntregas() + 1
                );
                repartidorRepository.save(delivery.getRepartidor());
            }
        }

        delivery = deliveryRepository.save(delivery);
        registrarEstado(delivery, nuevoEstado, descripcion, usuarioId);

        // Broadcast estado via WebSocket a todos los suscritos al delivery
        wsTemplate.convertAndSend(
            "/topic/delivery/" + deliveryId + "/estado",
            EstadoDeliveryEvent.builder()
                .deliveryId(deliveryId)
                .estado(nuevoEstado)
                .descripcion(descripcion)
                .timestamp(Instant.now())
                .build()
        );

        return deliveryMapper.toResponseDTO(delivery);
    }

    // =========================================================
    // CONFIRMACION POR OTP
    // =========================================================
    public void confirmarEntregaOTP(Long deliveryId, ConfirmarOTPDTO dto) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery no encontrado: " + deliveryId));

        if (delivery.getEstado() != DeliveryStatus.EN_CAMINO
                && delivery.getEstado() != DeliveryStatus.CERCA) {
            throw new BusinessException("Solo se puede confirmar cuando el repartidor está en camino o cerca");
        }

        if (delivery.getCodigoExpiraAt() == null || delivery.getCodigoExpiraAt().isBefore(Instant.now())) {
            throw new BusinessException("El código OTP ha expirado");
        }

        if (!passwordEncoder.matches(dto.getCodigo(), delivery.getCodigoConfirmacion())) {
            throw new BusinessException("Código OTP incorrecto");
        }

        cambiarEstado(deliveryId, DeliveryStatus.ENTREGADO, null, "Confirmado por código OTP");
    }

    // =========================================================
    // CONFIRMACION POR FOTO (usa tu CloudinaryService existente)
    // =========================================================
    public void confirmarEntregaFoto(Long deliveryId, MultipartFile foto, Long repartidorId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery no encontrado: " + deliveryId));

        if (delivery.getRepartidor() == null ||
                !delivery.getRepartidor().getIdRepartidor().equals(repartidorId)) {
            throw new BusinessException("No autorizado para confirmar este delivery");
        }

        // Sube la foto usando tu CloudinaryService existente
        String fotoUrl = cloudinaryService.uploadFile(foto, CLOUDINARY_FOLDER_DELIVERY);
        delivery.setFotoEntregaUrl(fotoUrl);
        deliveryRepository.save(delivery);

        cambiarEstado(deliveryId, DeliveryStatus.ENTREGADO, repartidorId, "Confirmado con foto de entrega");
    }

    // =========================================================
    // INTENTO FALLIDO (nadie abre la puerta)
    // =========================================================
    public void reportarIntentoFallido(Long deliveryId, MultipartFile foto, String motivo, Long repartidorId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery no encontrado: " + deliveryId));

        // Subir foto del intento fallido como evidencia
        if (foto != null && !foto.isEmpty()) {
            String fotoUrl = cloudinaryService.uploadFile(foto, CLOUDINARY_FOLDER_DELIVERY + "/fallidos");
            delivery.setFotoEntregaUrl(fotoUrl);
        }

        deliveryRepository.save(delivery);
        cambiarEstado(deliveryId, DeliveryStatus.FALLIDO, repartidorId,
            "Intento fallido: " + (motivo != null ? motivo : "Nadie recibió el pedido"));
    }

    // =========================================================
    // CALIFICACION DEL CLIENTE AL REPARTIDOR
    // =========================================================
    public void calificarEntrega(Long deliveryId, CalificacionDTO dto, Long clienteId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery no encontrado: " + deliveryId));

        if (delivery.getEstado() != DeliveryStatus.ENTREGADO) {
            throw new BusinessException("Solo se puede calificar un delivery entregado");
        }

        if (!delivery.getOrden().getUsuarioCliente().getId().equals(clienteId)) {
            throw new BusinessException("No autorizado para calificar este delivery");
        }

        if (delivery.getCalificacionCliente() != null) {
            throw new BusinessException("Ya calificaste esta entrega");
        }

        delivery.setCalificacionCliente(dto.getCalificacion());  // Short
        delivery.setComentarioCliente(dto.getComentario());
        deliveryRepository.save(delivery);

        // Recalcular promedio del repartidor
        recalcularCalificacionRepartidor(delivery.getRepartidor().getIdRepartidor());
    }

    // =========================================================
    // HELPERS PRIVADOS
    // =========================================================
    private String generarYGuardarOTP(Delivery delivery) {
        String codigo = String.format("%04d", new SecureRandom().nextInt(10000));
        delivery.setCodigoConfirmacion(passwordEncoder.encode(codigo));
        delivery.setCodigoExpiraAt(Instant.now().plus(OTP_EXPIRACION_HORAS, ChronoUnit.HOURS));
        deliveryRepository.save(delivery);
        return codigo; // solo se retorna aquí, nunca más
    }

    private void marcarTimestamp(Delivery delivery, DeliveryStatus estado) {
        Instant ahora = Instant.now();
        switch (estado) {
            case REPARTIDOR_ASIGNADO -> delivery.setAsignadoAt(ahora);
            case EN_TIENDA           -> delivery.setEnTiendaAt(ahora);
            case RECOGIDO            -> delivery.setRecogidoAt(ahora);
            case ENTREGADO           -> delivery.setEntregadoAt(ahora);
            default -> {}
        }
    }

    private void registrarEstado(Delivery delivery, DeliveryStatus estado,
                                  String descripcion, Long usuarioId) {
        estadoRepository.save(DeliveryEstado.builder()
            .delivery(delivery)
            .estado(estado)
            .descripcion(descripcion)
            .build());
    }

    private void recalcularCalificacionRepartidor(Long repartidorId) {
        // Promedio simple de todas las calificaciones recibidas
        Double promedio = deliveryRepository
            .findByRepartidorIdRepartidorOrderByCreatedAtDesc(repartidorId)
            .stream()
            .filter(d -> d.getCalificacionCliente() != null)
            .mapToInt(d -> d.getCalificacionCliente().intValue()) // Short → int
            .average()
            .orElse(5.0);

        repartidorRepository.findById(repartidorId).ifPresent(r -> {
            r.setCalificacionPromedio(new java.math.BigDecimal(String.format("%.2f", promedio)));
            repartidorRepository.save(r);
        });
    }
}
