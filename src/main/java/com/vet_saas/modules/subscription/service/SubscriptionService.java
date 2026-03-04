package com.vet_saas.modules.subscription.service;

import com.vet_saas.core.exceptions.types.ResourceNotFoundException;
import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.subscription.model.EstadoSuscripcion;
import com.vet_saas.modules.subscription.model.Plan;
import com.vet_saas.modules.subscription.model.Suscripcion;
import com.vet_saas.modules.subscription.repository.PlanRepository;
import com.vet_saas.modules.subscription.repository.SuscripcionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vet_saas.modules.subscription.dto.PlanResponseDto;
import com.vet_saas.modules.subscription.dto.SuscripcionResponseDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.vet_saas.modules.subscription.dto.SubscriptionUsageDto;
import com.vet_saas.modules.pet.repository.MascotaRepository;
import com.vet_saas.modules.catalog.repository.ProductoRepository;
import com.vet_saas.modules.payment.dto.PaymentPreferenceResponse;
import com.vet_saas.modules.payment.gateway.MercadoPagoGateway;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubscriptionService {
        private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SubscriptionService.class);

        private final SuscripcionRepository suscripcionRepository;
        private final PlanRepository planRepository;
        private final ProductoRepository productoRepository;
        private final com.vet_saas.modules.company.repository.EmpresaRepository empresaRepository;
        private final com.vet_saas.modules.veterinarian.repository.VeterinarioRepository veterinarioRepository;
        private final MascotaRepository mascotaRepository;
        private final MercadoPagoGateway mercadoPagoGateway;
        private final com.vet_saas.config.AppProperties appProperties;

        private Empresa getEmpresaFromUsuario(com.vet_saas.modules.user.model.Usuario usuario) {
                return empresaRepository.findByUsuarioPropietarioId(usuario.getId())
                                .orElseThrow(
                                                () -> new ResourceNotFoundException(
                                                                "No se encontró una empresa asociada perfil de usuario: "
                                                                                + usuario.getCorreo()
                                                                                + ". Por favor, complete sus datos de empresa."));
        }

        private com.vet_saas.modules.veterinarian.model.Veterinario getVeterinarioFromUsuario(
                        com.vet_saas.modules.user.model.Usuario usuario) {
                return veterinarioRepository.findByUsuarioId(usuario.getId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "No se encontró un perfil de veterinario asociado a este usuario."));
        }

        @Transactional(readOnly = true)
        public List<PlanResponseDto> getAvailablePlans() {
                return planRepository.findAllByActivoTrue().stream()
                                .map(PlanResponseDto::fromEntity)
                                .collect(Collectors.toList());
        }

        @Transactional
        public SuscripcionResponseDto getSuscripcionActual(com.vet_saas.modules.user.model.Usuario usuario) {
                if ("VETERINARIO".equals(usuario.getRol().name())) {
                        com.vet_saas.modules.veterinarian.model.Veterinario vet = getVeterinarioFromUsuario(usuario);
                        return SuscripcionResponseDto.fromEntity(getSuscripcionEntityByVeterinario(vet.getId()));
                } else {
                        Empresa empresa = getEmpresaFromUsuario(usuario);
                        return SuscripcionResponseDto.fromEntity(getSuscripcionEntityByEmpresa(empresa.getId()));
                }
        }

        @Transactional
        public Suscripcion getSuscripcionEntityByEmpresa(Long empresaId) {
                return suscripcionRepository.findByEmpresaId(empresaId)
                                .orElseGet(() -> {
                                        // Lazy assignment: Si no tiene, buscar empresa y asignar Básico
                                        com.vet_saas.modules.company.model.Empresa empresa = empresaRepository
                                                        .findById(empresaId)
                                                        .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                                        "Empresa no encontrada con ID: " + empresaId));
                                        return assignDefaultPlan(empresa);
                                });
        }

        @Transactional(readOnly = true)
        public Suscripcion getSuscripcionByEmpresa(Long empresaId) {
                return suscripcionRepository.findByEmpresaId(empresaId)
                                .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                "No se encontró una suscripción para la empresa ID: " + empresaId));
        }

        @Transactional
        public Suscripcion getSuscripcionEntityByVeterinario(Long veterinarioId) {
                return suscripcionRepository.findByVeterinarioId(veterinarioId)
                                .orElseGet(() -> {
                                        com.vet_saas.modules.veterinarian.model.Veterinario vet = veterinarioRepository
                                                        .findById(veterinarioId)
                                                        .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                                        "Veterinario no encontrado con ID: "
                                                                                        + veterinarioId));
                                        return assignDefaultPlanToVeterinario(vet);
                                });
        }

        @Transactional(readOnly = true)
        public Suscripcion getSuscripcionByVeterinario(Long veterinarioId) {
                return suscripcionRepository.findByVeterinarioId(veterinarioId)
                                .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                "No se encontró una suscripción para el veterinario ID: "
                                                                + veterinarioId));
        }

        @Transactional
        public Suscripcion assignDefaultPlan(Empresa empresa) {
                // Evitar duplicados si ya tiene suscripción
                if (suscripcionRepository.findByEmpresaId(empresa.getId()).isPresent()) {
                        return suscripcionRepository.findByEmpresaId(empresa.getId()).get();
                }

                Plan freePlan = planRepository.findByNombreIgnoreCase("Básico")
                                .or(() -> planRepository.findAllByActivoTrue().stream().findFirst())
                                .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                "No se encontraron planes activos en el sistema."));

                Suscripcion suscripcion = Suscripcion.builder()
                                .empresa(empresa)
                                .plan(freePlan)
                                .fechaInicio(LocalDateTime.now())
                                .estado(EstadoSuscripcion.ACTIVA)
                                .build();

                return suscripcionRepository.save(suscripcion);
        }

        @Transactional
        public Suscripcion assignDefaultPlanToVeterinario(com.vet_saas.modules.veterinarian.model.Veterinario vet) {
                if (suscripcionRepository.findByVeterinarioId(vet.getId()).isPresent()) {
                        return suscripcionRepository.findByVeterinarioId(vet.getId()).get();
                }

                Plan freePlan = planRepository.findByNombreIgnoreCase("Básico")
                                .or(() -> planRepository.findAllByActivoTrue().stream().findFirst())
                                .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                "No se encontraron planes activos en el sistema."));

                Suscripcion suscripcion = Suscripcion.builder()
                                .veterinario(vet)
                                .plan(freePlan)
                                .fechaInicio(LocalDateTime.now())
                                .estado(EstadoSuscripcion.ACTIVA)
                                .build();

                return suscripcionRepository.save(suscripcion);
        }

        @Transactional
        public SuscripcionResponseDto updatePlan(Long empresaId, Long planId) {
                return updatePlanForOwner(empresaId, null, planId);
        }

        @Transactional
        public SuscripcionResponseDto updatePlanForUsuario(com.vet_saas.modules.user.model.Usuario usuario,
                        Long planId) {
                if ("VETERINARIO".equals(usuario.getRol().name())) {
                        com.vet_saas.modules.veterinarian.model.Veterinario vet = getVeterinarioFromUsuario(usuario);
                        return updatePlanForOwner(null, vet.getId(), planId);
                } else {
                        Empresa empresa = getEmpresaFromUsuario(usuario);
                        return updatePlanForOwner(empresa.getId(), null, planId);
                }
        }

        @Transactional
        public SuscripcionResponseDto updatePlanForVeterinario(Long veterinarioId, Long planId) {
                return updatePlanForOwner(null, veterinarioId, planId);
        }

        private SuscripcionResponseDto updatePlanForOwner(Long empresaId, Long veterinarioId, Long planId) {
                Suscripcion suscripcion;
                if (empresaId != null) {
                        suscripcion = getSuscripcionEntityByEmpresa(empresaId);
                } else {
                        suscripcion = getSuscripcionEntityByVeterinario(veterinarioId);
                }

                Plan newPlan = planRepository.findById(planId)
                                .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                "Plan no encontrado con ID: " + planId));

                suscripcion.setPlan(newPlan);
                suscripcion.setUpdatedAt(LocalDateTime.now());

                return SuscripcionResponseDto.fromEntity(suscripcionRepository.save(suscripcion));
        }

        /**
         * Verifica si una empresa puede agregar una mascota según su plan.
         * 
         * @param currentCount Cantidad actual de mascotas activas de la empresa.
         */
        public boolean canAddMascota(Long empresaId, long currentCount) {
                Suscripcion sub = getSuscripcionByEmpresa(empresaId);
                Integer limit = sub.getPlan().getLimiteMascotas();
                return limit == 0 || currentCount < limit;
        }

        /**
         * Verifica si una empresa puede agregar un producto según su plan.
         * 
         * @param currentCount Cantidad actual de productos activos de la empresa.
         */
        public boolean canAddProduct(Long empresaId, long currentCount) {
                Suscripcion sub = getSuscripcionByEmpresa(empresaId);
                Integer limit = sub.getPlan().getLimiteProductos();
                return limit == 0 || currentCount < limit;
        }

        @Transactional(readOnly = true)
        public SubscriptionUsageDto getUsageMetrics(com.vet_saas.modules.user.model.Usuario usuario) {
                Empresa empresa = getEmpresaFromUsuario(usuario);
                Suscripcion sub = getSuscripcionByEmpresa(empresa.getId());
                Plan plan = sub.getPlan();

                // El dueño de la empresa es el que posee las mascotas "de la empresa" en este
                // modelo
                long petCount = mascotaRepository.countByUsuarioIdAndActivoTrue(usuario.getId());
                long productCount = productoRepository.countByEmpresaIdAndActivoTrue(empresa.getId());

                return SubscriptionUsageDto.builder()
                                .currentPets(petCount)
                                .maxPets(plan.getLimiteMascotas())
                                .petPercentage(calculatePercentage(petCount, plan.getLimiteMascotas()))
                                .currentProducts(productCount)
                                .maxProducts(plan.getLimiteProductos())
                                .productPercentage(calculatePercentage(productCount, plan.getLimiteProductos()))
                                .build();
        }

        @Transactional
        public PaymentPreferenceResponse createSubscriptionCheckout(com.vet_saas.modules.user.model.Usuario usuario,
                        Long planId) {
                Long entityId;
                String entityName;
                String prefix;

                if ("VETERINARIO".equals(usuario.getRol().name())) {
                        com.vet_saas.modules.veterinarian.model.Veterinario vet = getVeterinarioFromUsuario(usuario);
                        entityId = vet.getId();
                        entityName = vet.getNombres() + " " + vet.getApellidos();
                        prefix = "VET";
                } else {
                        Empresa empresa = getEmpresaFromUsuario(usuario);
                        entityId = empresa.getId();
                        entityName = empresa.getNombreComercial();
                        prefix = "EMP";
                }
                Plan plan = planRepository.findById(planId)
                                .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                "Plan no encontrado con ID: " + planId));

                if (plan.getPrecioMensual().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new com.vet_saas.core.exceptions.types.BusinessException(
                                        "El plan seleccionado es gratuito. Use el flujo de cambio directo.");
                }

                // Configurar Items para Mercado Pago
                PreferenceItemRequest item = PreferenceItemRequest.builder()
                                .id(plan.getId().toString())
                                .title("Suscripción VetSaaS: Plan " + plan.getNombre())
                                .description(plan.getDescripcion())
                                .quantity(1)
                                .unitPrice(new BigDecimal(plan.getPrecioMensual().toString()))
                                .build();

                PreferencePayerRequest payer = PreferencePayerRequest.builder()
                                .email(usuario.getCorreo())
                                .name(entityName)
                                .build();

                // Metadata para procesar el pago luego
                Map<String, Object> metadata = java.util.Map.of(
                                "type", "SUBSCRIPTION",
                                prefix.equals("EMP") ? "empresa_id" : "veterinario_id", entityId,
                                "plan_id", plan.getId(),
                                "user_id", usuario.getId());

                String successUrl = appProperties.getExternal().getFrontendUrl()
                                + "/portal/" + (prefix.equals("EMP") ? "empresa" : "veterinario") + "/pago-exitoso";
                String notificationUrl = appProperties.getExternal().getBackendUrl() + "/api/v1/payments/webhook";

                return mercadoPagoGateway.createPreference(
                                appProperties.getExternal().getMercadoPago().getAccessToken(),
                                "SUB" + prefix + "-" + entityId + "-" + System.currentTimeMillis(),
                                Collections.singletonList(item),
                                payer,
                                metadata,
                                successUrl,
                                notificationUrl);
        }

        @Transactional
        public void processSubscriptionPayment(Long empresaId, Long veterinarioId, Long planId, String mpPaymentId) {
                // Verificar si este pago ya fue procesado (Idempotencia)
                Suscripcion suscripcionExistente;

                if (empresaId != null) {
                        suscripcionExistente = getSuscripcionByEmpresa(empresaId);
                } else {
                        suscripcionExistente = getSuscripcionByVeterinario(veterinarioId);
                }

                if (suscripcionExistente.getMpPreapprovalId() != null &&
                                suscripcionExistente.getMpPreapprovalId().equals(mpPaymentId)) {
                        LOGGER.info("El pago {} ya fue procesado. Omitiendo.", mpPaymentId);
                        return;
                }

                LOGGER.info("Procesando pago de suscripción al plan {}. PaymentId: {}", planId, mpPaymentId);

                Plan plan = planRepository.findById(planId)
                                .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                "Plan no encontrado con ID: " + planId));

                Suscripcion suscripcion;
                if (empresaId != null) {
                        Empresa empresa = empresaRepository.findById(empresaId)
                                        .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                        "Empresa no encontrada con ID: " + empresaId));
                        suscripcion = suscripcionRepository.findByEmpresaId(empresaId)
                                        .orElseGet(() -> Suscripcion.builder()
                                                        .empresa(empresa)
                                                        .fechaInicio(LocalDateTime.now())
                                                        .build());
                } else {
                        com.vet_saas.modules.veterinarian.model.Veterinario vet = veterinarioRepository
                                        .findById(veterinarioId)
                                        .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                                                        "Veterinario no encontrado con ID: " + veterinarioId));
                        suscripcion = suscripcionRepository.findByVeterinarioId(veterinarioId)
                                        .orElseGet(() -> Suscripcion.builder()
                                                        .veterinario(vet)
                                                        .fechaInicio(LocalDateTime.now())
                                                        .build());
                }

                suscripcion.setPlan(plan);
                suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
                suscripcion.setFechaInicio(LocalDateTime.now());
                suscripcion.setFechaFin(LocalDateTime.now().plusMonths(1));
                suscripcion.setUpdatedAt(LocalDateTime.now());

                suscripcionRepository.save(suscripcion);
                LOGGER.info("Suscripción actualizada exitosamente.");
        }

        private double calculatePercentage(long current, int max) {
                if (max <= 0)
                        return 0; // Ilimitado o error
                double percent = (double) current / max * 100;
                return Math.min(percent, 100.0);
        }
}
