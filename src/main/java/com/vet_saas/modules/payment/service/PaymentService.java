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
import com.vet_saas.modules.user.model.Usuario;
import com.vet_saas.modules.veterinarian.model.Veterinario;
import com.vet_saas.modules.veterinarian.repository.VeterinarioRepository;
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
    private final VeterinarioRepository veterinarioRepository;
    private final com.vet_saas.modules.subscription.service.SubscriptionService subscriptionService;

    @Transactional
    public PaymentPreferenceResponse createCheckoutUrl(Long ordenId, Usuario usuarioActual) {
        LOGGER.info("Iniciando generación de checkout para ordenId: {}", ordenId);

        Orden orden = ordenRepository.findByIdWithDetails(ordenId)
                .orElseThrow(() -> new BusinessException("Orden no encontrada"));

        if (!orden.getUsuarioCliente().getId().equals(usuarioActual.getId())) {
            LOGGER.warn("Usuario {} intentó pagar la orden {} que no le pertenece", usuarioActual.getId(), ordenId);
            throw new ForbiddenException("No tienes permiso para procesar esta orden.");
        }

        if (orden.getEstado() != EstadoOrden.PENDIENTE) {
            LOGGER.warn("Abortando checkout: La orden {} ya está en estado {}", ordenId, orden.getEstado());
            throw new BusinessException("La orden ya fue procesada o no está disponible para pago");
        }

        String accessToken;
        Long vendorId;
        String vendorType;

        if (orden.getEmpresa() != null) {
            Empresa empresa = orden.getEmpresa();
            if (empresa.getMpAccessToken() == null || empresa.getMpAccessToken().isBlank()) {
                LOGGER.error("La empresa {} no tiene Access Token configurado", empresa.getId());
                throw new BusinessException("La empresa no tiene configurada su cuenta de MercadoPago.");
            }
            accessToken = cryptoUtil.decrypt(empresa.getMpAccessToken());
            vendorId = empresa.getId();
            vendorType = "EMPRESA";
        } else if (orden.getVeterinario() != null) {
            Veterinario veterinario = orden.getVeterinario();
            if (veterinario.getMpAccessToken() == null || veterinario.getMpAccessToken().isBlank()) {
                LOGGER.error("El veterinario {} no tiene Access Token configurado", veterinario.getId());
                throw new BusinessException("Este veterinario no tiene configurada su cuenta de MercadoPago.");
            }
            accessToken = cryptoUtil.decrypt(veterinario.getMpAccessToken());
            vendorId = veterinario.getId();
            vendorType = "VETERINARIO";
        } else {
            throw new BusinessException("La orden no tiene un vendedor (Empresa/Veterinario) asociado.");
        }

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

            boolean isSandbox = appProperties.getExternal().getMercadoPago().isSandbox();
            String payerEmail = null;

            if (isSandbox) {
                String configuredEmail = appProperties.getExternal().getMercadoPago().getSandboxBuyerEmail();
                if (configuredEmail != null && !configuredEmail.isBlank()) {
                    payerEmail = configuredEmail;
                    LOGGER.info("Sandbox detectado: usando email de prueba '{}' como payer", payerEmail);
                } else {
                    LOGGER.info("Sandbox detectado: dejando email en blanco para ingreso manual en el checkout");
                }
            } else {
                payerEmail = orden.getUsuarioCliente().getCorreo();
            }

            PreferencePayerRequest.PreferencePayerRequestBuilder payerBuilder = PreferencePayerRequest.builder();
            if (payerEmail != null) {
                payerBuilder.email(payerEmail);
            }

            if (perfil != null) {
                payerBuilder.name(perfil.getNombres() + " " + perfil.getApellidos());
            } else {
                payerBuilder.name("Cliente Huella360");
            }

            PreferencePayerRequest payer = payerBuilder.build();

            String webhookBase = appProperties.getExternal().getBackendUrl();
            String notificationUrl = webhookBase + "/api/v1/payments/webhook/"
                    + (orden.getEmpresa() != null ? orden.getEmpresa().getId()
                            : "vet_" + orden.getVeterinario().getId());

            PaymentPreferenceResponse response = mpGateway.createPreference(
                    accessToken,
                    orden.getCodigoOrden(),
                    items,
                    payer,
                    Map.of(
                            "orden_id", orden.getId(),
                            "vendor_id", vendorId,
                            "vendor_type", vendorType),
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
    public void processWebhook(String paymentId, String pathEmpresaId) {
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
                if (pathEmpresaId.startsWith("vet_")) {
                    Long vetId = Long.parseLong(pathEmpresaId.substring(4));
                    Veterinario veterinario = veterinarioRepository.findById(vetId)
                            .orElseThrow(() -> new BusinessException("Veterinario " + vetId + " no encontrado"));
                    tokenToUse = cryptoUtil.decrypt(veterinario.getMpAccessToken());
                } else {
                    Long empId = Long.parseLong(pathEmpresaId);
                    Empresa empresa = empresaRepository.findById(empId)
                            .orElseThrow(() -> new BusinessException("Empresa " + empId + " no encontrada"));
                    tokenToUse = cryptoUtil.decrypt(empresa.getMpAccessToken());
                }
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

        // Convertir de forma segura ya que MP puede enviar números como decimales
        // (ej:1.0)
        Long empresaId = metadata.containsKey("empresa_id")
                ? Double.valueOf(metadata.get("empresa_id").toString()).longValue()
                : null;
        Long veterinarioId = metadata.containsKey("veterinario_id")
                ? Double.valueOf(metadata.get("veterinario_id").toString()).longValue()
                : null;
        Long planId = Double.valueOf(metadata.get("plan_id").toString()).longValue();

        subscriptionService.processSubscriptionPayment(empresaId, veterinarioId, planId, payment.getId().toString());
    }

    private void handleOrderWebhook(Payment payment, Map<String, Object> metadata, String pathEmpresaId) {
        if (metadata.get("vendor_id") == null) {
            LOGGER.error("El pago {} no contiene metadata de vendor_id", payment.getId());
            return;
        }

        Long vendorId = Double.valueOf(metadata.get("vendor_id").toString()).longValue();
        String vendorType = metadata.get("vendor_type") != null ? metadata.get("vendor_type").toString() : "EMPRESA";

        if (pathEmpresaId != null) {
            String mpVendorKey = "VETERINARIO".equals(vendorType) ? "vet_" + vendorId : vendorId.toString();
            if (!mpVendorKey.equals(pathEmpresaId)) {
                LOGGER.error("MIX DE TENANT DETECTADO. mpVendorKey ({}) != pathEmpresaId ({})", mpVendorKey,
                        pathEmpresaId);
                throw new ForbiddenException("El pago no pertenece a este vendedor.");
            }
        }

        // 4. Lock the order row — serializes concurrent webhook processing
        Orden orden = ordenRepository.findByCodigoOrdenForUpdate(payment.getExternalReference())
                .orElseThrow(() -> new BusinessException("Orden no encontrada: " + payment.getExternalReference()));

        // 5. Re-check idempotency AFTER acquiring lock
        if (pagoRepository.existsByMpPaymentId(payment.getId().toString())) {
            LOGGER.info("Webhook duplicado ignorado (post-lock idempotencia). paymentId: {}", payment.getId());
            return;
        }

        // 6. Mapeo de Estados
        String mpStatus = payment.getStatus();
        EstadoOrden nuevoEstado = switch (mpStatus) {
            case "approved" -> EstadoOrden.PAGADO;
            case "rejected" -> EstadoOrden.FALLIDO;
            case "cancelled" -> EstadoOrden.CANCELADO;
            default -> orden.getEstado();
        };

        // 7. Actualización de Base de Datos
        if (nuevoEstado != orden.getEstado()) {
            orden.setEstado(nuevoEstado);
            orden.setMetodoPago(payment.getPaymentMethodId());
            ordenRepository.save(orden);
            LOGGER.info("Orden {} actualizada al estado {}", orden.getCodigoOrden(), nuevoEstado);
        }

        // 8. Registro de Pago
        Empresa empresa = null;
        Veterinario veterinario = null;
        if ("EMPRESA".equals(vendorType)) {
            empresa = empresaRepository.findById(vendorId).orElseThrow();
        } else {
            veterinario = veterinarioRepository.findById(vendorId).orElseThrow();
        }

        Pago pagoModel = Pago.builder()
                .empresa(empresa)
                .veterinario(veterinario)
                .orden(orden)
                .mpPaymentId(payment.getId().toString())
                .monto(payment.getTransactionAmount())
                .metodoPago(payment.getPaymentMethodId())
                .estado(mpStatus)
                .build();

        pagoRepository.save(pagoModel);

        // 9. Evento de Negocio
        if (nuevoEstado == EstadoOrden.PAGADO) {
            eventPublisher.publishEvent(new OrderPaidEvent(this, orden));
        }
    }
}