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
import com.vet_saas.modules.delivery.model.*;
import com.vet_saas.modules.delivery.repository.DeliveryEstadoRepository;
import com.vet_saas.modules.delivery.repository.DeliveryRepository;
import com.vet_saas.modules.delivery.repository.RepartidorRepository;
import com.vet_saas.modules.notification.service.EmailService;
import com.vet_saas.modules.sales.model.Orden;
import com.vet_saas.modules.user.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEstadoRepository estadoRepository;
    private final RepartidorRepository repartidorRepository;
    private final UsuarioRepository usuarioRepository;
    private final AsignacionService asignacionService;
    private final StorageService cloudinaryService;
    private final SimpMessagingTemplate wsTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DeliveryMapper deliveryMapper;
    private final EmailService emailService;

    private static final int OTP_EXPIRACION_HORAS = 4;
    private static final String CLOUDINARY_FOLDER_DELIVERY = "deliveries/confirmaciones";

    // =========================================================
    // CREAR DELIVERY (llamado desde OrdenService al crear orden)
    // =========================================================
    /**
     * Crear el delivery. Se usa REQUIRES_NEW para asegurar que el flush
     * y el commit ocurran independientemente del listener principal.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryResponseDTO crearDelivery(Orden orden, CrearDeliveryDTO dto) {
        if (deliveryRepository.existsByOrdenId(orden.getId())) {
            log.warn("Delivery ya existe para la orden {}", orden.getId());
            return null;
        }

        // Calcular distancia y tiempo estimado
        double distanciaKm = calcularDistanciaHaversine(
            dto.getOrigenLat().doubleValue(), dto.getOrigenLng().doubleValue(),
            dto.getDestinoLat().doubleValue(), dto.getDestinoLng().doubleValue()
        );
        int tiempoEstimadoMin = Math.max(10, (int) Math.ceil(distanciaKm / 0.5)); // ~30 km/h promedio en moto urbana

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
            .distanciaKm(new java.math.BigDecimal(String.format("%.2f", distanciaKm)))
            .tiempoEstimadoMin(tiempoEstimadoMin)
            .estado(DeliveryStatus.BUSCANDO_REPARTIDOR)
            .intentosAsignacion(0)
            .build();

        delivery = deliveryRepository.saveAndFlush(delivery);
        registrarEstado(delivery, DeliveryStatus.BUSCANDO_REPARTIDOR, "Delivery creado por pago de orden", null);

        String otpPlano = generarYGuardarOTP(delivery);

        // Enviar código OTP al cliente por email
        try {
            emailService.sendDeliveryOtpEmail(orden.getId(), otpPlano);
        } catch (Exception e) {
            log.warn("No se pudo enviar OTP al cliente de la orden {}: {}", orden.getCodigoOrden(), e.getMessage());
        }

        // Notificar al pool de repartidores
        wsTemplate.convertAndSend("/topic/pedidos-disponibles", deliveryMapper.toResponseDTO(delivery));

        // Retornar con OTP incluido (solo esta vez)
        return deliveryMapper.toResponseDTOConOTP(delivery, otpPlano);
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponseDTO> getPedidosDisponibles() {
        return deliveryRepository.findByEstado(DeliveryStatus.BUSCANDO_REPARTIDOR)
            .stream()
            .map(deliveryMapper::toResponseDTO)
            .collect(Collectors.toList());
    }

    public DeliveryResponseDTO aceptarPedido(Long deliveryId, Long usuarioId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery no encontrado"));

        if (delivery.getEstado() != DeliveryStatus.BUSCANDO_REPARTIDOR) {
            throw new BusinessException("El pedido ya no está disponible para asignación");
        }

        Repartidor repartidor = repartidorRepository.findByUsuarioId(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("No eres un repartidor registrado"));

        // Verificar que no tenga otro delivery activo
        boolean tieneActivo = deliveryRepository.existsByRepartidorIdAndEstadoNotIn(
            repartidor.getIdRepartidor(), 
            List.of(DeliveryStatus.ENTREGADO, DeliveryStatus.FALLIDO, DeliveryStatus.CANCELADO)
        );
        if (tieneActivo) {
            throw new BusinessException("Ya tienes un delivery en curso. Termínalo antes de tomar otro.");
        }

        delivery.setRepartidor(repartidor);
        delivery.setEstado(DeliveryStatus.REPARTIDOR_ASIGNADO);
        delivery.setAsignadoAt(Instant.now());
        deliveryRepository.save(delivery);

        repartidor.setEstadoActual(RepartidorStatus.OCUPADO);
        repartidorRepository.save(repartidor);

        registrarEstado(delivery, DeliveryStatus.REPARTIDOR_ASIGNADO, "Pedido aceptado por el repartidor", usuarioId);

        // Notificar al cliente y actualizar el pool
        wsTemplate.convertAndSend("/topic/delivery/" + deliveryId + "/estado", "REPARTIDOR_ASIGNADO");
        wsTemplate.convertAndSend("/topic/pedidos-pool-update", deliveryId);

        return deliveryMapper.toResponseDTO(delivery);
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

    /**
     * Calcula la distancia en kilómetros entre dos puntos usando la fórmula de Haversine.
     */
    private double calcularDistanciaHaversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0; // Radio de la Tierra en km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
