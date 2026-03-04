package com.vet_saas.modules.payment.service;

import com.mercadopago.client.preference.*;
import com.mercadopago.resources.payment.Payment;
import com.vet_saas.config.AppProperties;
import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.core.exceptions.types.ForbiddenException;
import com.vet_saas.core.utils.CryptoUtil;
import com.vet_saas.modules.client.repository.ClienteRepository;
import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.company.repository.EmpresaRepository;
import com.vet_saas.modules.payment.dto.PaymentPreferenceResponse;
import com.vet_saas.modules.payment.gateway.MercadoPagoGateway;
import com.vet_saas.modules.payment.model.Pago;
import com.vet_saas.modules.payment.repository.PagoRepository;
import com.vet_saas.modules.sales.event.OrderPaidEvent;
import com.vet_saas.modules.sales.model.EstadoOrden;
import com.vet_saas.modules.sales.model.Orden;
import com.vet_saas.modules.sales.repository.OrdenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentService.class);

    private final OrdenRepository ordenRepository;
    private final EmpresaRepository empresaRepository;
    private final PagoRepository pagoRepository;
    private final AppProperties appProperties;
    private final MercadoPagoGateway mpGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final CryptoUtil cryptoUtil;
    private final ClienteRepository clienteRepository;
    private final com.vet_saas.modules.subscription.service.SubscriptionService subscriptionService;

    @Transactional
    public PaymentPreferenceResponse createCheckoutUrl(Long ordenId) {
        LOGGER.info("Iniciando generación de checkout para ordenId: {}", ordenId);

        Orden orden = ordenRepository.findByIdWithDetails(ordenId)
                .orElseThrow(() -> new BusinessException("Orden no encontrada"));

        if (orden.getEstado() != EstadoOrden.PENDIENTE) {
            LOGGER.warn("Abortando checkout: La orden {} ya está en estado {}", ordenId, orden.getEstado());
            throw new BusinessException("La orden ya fue procesada o no está disponible para pago");
        }

        Empresa empresa = orden.getEmpresa();

        // Verificación de existencia de Token
        if (empresa.getMpAccessToken() == null || empresa.getMpAccessToken().isBlank()) {
            LOGGER.error("La empresa {} no tiene Access Token configurado", empresa.getId());
            throw new BusinessException("La empresa no tiene configurada su cuenta de MercadoPago.");
        }

        String decryptedToken = cryptoUtil.decrypt(empresa.getMpAccessToken());

        // Fetch profile to get real names
        com.vet_saas.modules.client.model.PerfilCliente perfil = clienteRepository
                .findByUsuarioId(orden.getUsuarioCliente().getId())
                .orElse(null);

        try {
            List<PreferenceItemRequest> items = new ArrayList<>(orden.getDetalles().stream()
                    .map(detalle -> PreferenceItemRequest.builder()
                            .id(detalle.getProducto().getId().toString())
                            .title(detalle.getProducto().getNombre())
                            .quantity(detalle.getCantidad())
                            .currencyId("PEN")
                            .unitPrice(detalle.getPrecioUnitario())
                            .build())
                    .toList());

            if (orden.getCostoEnvio() != null && orden.getCostoEnvio().compareTo(BigDecimal.ZERO) > 0) {
                items.add(PreferenceItemRequest.builder()
                        .id("SHIP-001")
                        .title("Costo de Envío")
                        .quantity(1)
                        .currencyId("PEN")
                        .unitPrice(orden.getCostoEnvio())
                        .build());
            }

            // Payer: Correo real del comprador y nombres de su perfil
            PreferencePayerRequest.PreferencePayerRequestBuilder payerBuilder = PreferencePayerRequest.builder()
                    .email(orden.getUsuarioCliente().getCorreo());

            if (perfil != null) {
                payerBuilder.name(perfil.getNombres() + " " + perfil.getApellidos());
            } else {
                payerBuilder.name("Cliente Vetsaas");
            }

            PreferencePayerRequest payer = payerBuilder.build();

            String webhookBase = appProperties.getExternal().getBackendUrl();
            String notificationUrl = webhookBase + "/api/v1/payments/webhook/" + empresa.getId();

            PaymentPreferenceResponse response = mpGateway.createPreference(
                    decryptedToken,
                    orden.getCodigoOrden(),
                    items,
                    payer,
                    Map.of("orden_id", orden.getId(), "empresa_id", empresa.getId()),
                    appProperties.getExternal().getFrontendUrl() + "/marketplace/success",
                    notificationUrl);

            orden.setMpPreferenceId(response.preferenceId());
            ordenRepository.save(orden);

            LOGGER.info("Checkout generado exitosamente. preferenceId={} orden={}", response.preferenceId(),
                    orden.getCodigoOrden());

            return response;

        } catch (Exception ex) {
            LOGGER.error("Error creando preferencia para orden {}", ordenId, ex);
            throw new BusinessException("Error interno al procesar el pago");
        }
    }

    @Transactional
    public void processWebhook(String paymentId, Long pathEmpresaId) {
        LOGGER.info("Webhook recibido. paymentId: {} empresaId: {}", paymentId, pathEmpresaId);

        // 1. Idempotencia
        if (pagoRepository.existsByMpPaymentId(paymentId)) {
            LOGGER.info("Webhook ignorado por idempotencia. paymentId: {}", paymentId);
            return;
        }

        try {
            // 3. Obtener token para consulta (Empresa o Plataforma)
            Map<String, Object> metadata = null;
            String tokenToUse = null;

            // Intento inicial con token de plataforma para ver metadata si pathEmpresaId es
            // null
            if (pathEmpresaId == null) {
                tokenToUse = appProperties.getExternal().getMercadoPago().getAccessToken();
            } else {
                Empresa empresa = empresaRepository.findById(pathEmpresaId)
                        .orElseThrow(() -> new BusinessException("Empresa no encontrada"));
                tokenToUse = cryptoUtil.decrypt(empresa.getMpAccessToken());
            }

            // 2. Consulta a Mercado Pago
            Payment payment = mpGateway.getPaymentDetails(paymentId, tokenToUse);
            metadata = payment.getMetadata();

            if (metadata == null) {
                LOGGER.error("El pago {} no contiene metadata", paymentId);
                return;
            }

            String type = metadata.get("type") != null ? metadata.get("type").toString() : "ORDER";

            // 4. Manejo por tipo de pago
            if ("SUBSCRIPTION".equals(type)) {
                handleSubscriptionWebhook(payment, metadata);
            } else {
                handleOrderWebhook(payment, metadata, pathEmpresaId);
            }

        } catch (Exception ex) {
            LOGGER.error("Error crítico al procesar el pago {}", paymentId, ex);
        }
    }

    private void handleSubscriptionWebhook(Payment payment, Map<String, Object> metadata) {
        if (!"approved".equals(payment.getStatus())) {
            LOGGER.info("Pago de suscripción {} no aprobado (estado: {})", payment.getId(), payment.getStatus());
            return;
        }

        // Convertir de forma segura ya que MP puede enviar números como decimales (ej:
        // 1.0)
        Long empresaId = metadata.containsKey("empresa_id")
                ? Double.valueOf(metadata.get("empresa_id").toString()).longValue()
                : null;
        Long veterinarioId = metadata.containsKey("veterinario_id")
                ? Double.valueOf(metadata.get("veterinario_id").toString()).longValue()
                : null;
        Long planId = Double.valueOf(metadata.get("plan_id").toString()).longValue();

        subscriptionService.processSubscriptionPayment(empresaId, veterinarioId, planId, payment.getId().toString());
    }

    private void handleOrderWebhook(Payment payment, Map<String, Object> metadata, Long pathEmpresaId) {
        if (metadata.get("empresa_id") == null) {
            LOGGER.error("El pago {} no contiene metadata de empresa_id", payment.getId());
            return;
        }

        Long mpEmpresaId = Double.valueOf(metadata.get("empresa_id").toString()).longValue();
        if (pathEmpresaId != null && !mpEmpresaId.equals(pathEmpresaId)) {
            LOGGER.error("MIX DE TENANT DETECTADO. mpEmpresaId ({}) != pathEmpresaId ({})", mpEmpresaId,
                    pathEmpresaId);
            throw new ForbiddenException("El pago no pertenece a esta empresa.");
        }

        // 4. Buscar Orden
        Orden orden = ordenRepository.findByCodigoOrden(payment.getExternalReference())
                .orElseThrow(() -> new BusinessException("Orden no encontrada: " + payment.getExternalReference()));

        // 5. Mapeo de Estados
        String mpStatus = payment.getStatus();
        EstadoOrden nuevoEstado = switch (mpStatus) {
            case "approved" -> EstadoOrden.PAGADO;
            case "rejected" -> EstadoOrden.FALLIDO;
            case "cancelled" -> EstadoOrden.CANCELADO;
            default -> orden.getEstado();
        };

        // 6. Actualización de Base de Datos
        if (nuevoEstado != orden.getEstado()) {
            orden.setEstado(nuevoEstado);
            orden.setMetodoPago(payment.getPaymentMethodId());
            ordenRepository.save(orden);
            LOGGER.info("Orden {} actualizada al estado {}", orden.getCodigoOrden(), nuevoEstado);
        }

        // 7. Registro de Pago (Evidencia)
        Empresa empresa = empresaRepository.findById(mpEmpresaId).orElseThrow();
        Pago pagoModel = Pago.builder()
                .empresa(empresa)
                .orden(orden)
                .mpPaymentId(payment.getId().toString())
                .monto(payment.getTransactionAmount())
                .metodoPago(payment.getPaymentMethodId())
                .estado(mpStatus)
                .build();

        pagoRepository.save(pagoModel);

        // 8. Evento de Negocio
        if (nuevoEstado == EstadoOrden.PAGADO) {
            eventPublisher.publishEvent(new OrderPaidEvent(this, orden));
        }
    }
}