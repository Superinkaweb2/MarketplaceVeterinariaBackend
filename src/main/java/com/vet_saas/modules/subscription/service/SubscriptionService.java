package com.vet_saas.modules.subscription.service;

import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.subscription.model.EstadoSuscripcion;
import com.vet_saas.modules.subscription.model.Plan;
import com.vet_saas.modules.subscription.model.Suscripcion;
import com.vet_saas.modules.subscription.repository.PlanRepository;
import com.vet_saas.modules.subscription.repository.SuscripcionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import com.vet_saas.modules.subscription.dto.SubscriptionUsageDto;
import com.vet_saas.modules.pet.repository.MascotaRepository;
import com.vet_saas.modules.catalog.repository.ProductoRepository;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SuscripcionRepository suscripcionRepository;
    private final PlanRepository planRepository;
    private final ProductoRepository productoRepository;
    private final com.vet_saas.modules.company.repository.EmpresaRepository empresaRepository;
    private final MascotaRepository mascotaRepository;

    private Empresa getEmpresaFromUsuario(com.vet_saas.modules.user.model.Usuario usuario) {
        return empresaRepository.findByUsuarioPropietarioId(usuario.getId())
                .orElseThrow(() -> new RuntimeException("No se encontró una empresa asociada a este usuario."));
    }

    public List<Plan> getAvailablePlans() {
        return planRepository.findAllByActivoTrue();
    }

    public Suscripcion getSuscripcionActual(com.vet_saas.modules.user.model.Usuario usuario) {
        Empresa empresa = getEmpresaFromUsuario(usuario);
        return getSuscripcionByEmpresa(empresa.getId());
    }

    public Suscripcion getSuscripcionByEmpresa(Long empresaId) {
        return suscripcionRepository.findByEmpresaId(empresaId)
                .orElseGet(() -> {
                    // Lazy assignment: Si no tiene, buscar empresa y asignar Básico
                    com.vet_saas.modules.company.model.Empresa empresa = empresaRepository.findById(empresaId)
                            .orElseThrow(() -> new RuntimeException("Empresa no encontrada: " + empresaId));
                    return assignDefaultPlan(empresa);
                });
    }

    @Transactional
    public Suscripcion assignDefaultPlan(Empresa empresa) {
        // Evitar duplicados si ya tiene suscripción
        if (suscripcionRepository.findByEmpresaId(empresa.getId()).isPresent()) {
            return suscripcionRepository.findByEmpresaId(empresa.getId()).get();
        }

        Plan freePlan = planRepository.findByNombre("Básico")
                .orElseThrow(() -> new RuntimeException(
                        "Plan Básico no encontrado. Asegúrese de que las migraciones fallaron o el plan no existe."));

        Suscripcion suscripcion = Suscripcion.builder()
                .empresa(empresa)
                .plan(freePlan)
                .fechaInicio(LocalDateTime.now())
                .estado(EstadoSuscripcion.ACTIVA)
                .build();

        return suscripcionRepository.save(suscripcion);
    }

    @Transactional
    public Suscripcion updatePlan(Long empresaId, Long planId) {
        Suscripcion suscripcion = getSuscripcionByEmpresa(empresaId);
        Plan newPlan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado"));

        suscripcion.setPlan(newPlan);
        suscripcion.setUpdatedAt(LocalDateTime.now());

        return suscripcionRepository.save(suscripcion);
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

    private double calculatePercentage(long current, int max) {
        if (max <= 0)
            return 0; // Ilimitado o error
        double percent = (double) current / max * 100;
        return Math.min(percent, 100.0);
    }
}
