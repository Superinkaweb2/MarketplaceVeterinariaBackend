package com.vet_saas.modules.subscription.service;

import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.modules.subscription.model.Plan;
import com.vet_saas.modules.subscription.model.Suscripcion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanEnforcementService {

    private final SubscriptionService subscriptionService;

    @Transactional(readOnly = true)
    public void enforceMascotaLimit(Long usuarioId, long currentCount, String role) {
        if ("CLIENTE".equals(role)) {
            enforceClientPetLimit(usuarioId, currentCount);
        }
    }

    private void enforceClientPetLimit(Long usuarioId, long currentCount) {
        Suscripcion sub = subscriptionService.getSuscripcionByUsuarioId(usuarioId);
        if (sub == null) return;
        Integer limit = sub.getPlan().getLimiteMascotas();
        if (limit != null && limit > 0 && currentCount >= limit) {
            throw new BusinessException(
                    "Has alcanzado el límite de " + limit + " mascota(s) de tu plan " +
                    sub.getPlan().getNombre() + ". Mejora tu suscripción para agregar más.");
        }
    }

    @Transactional(readOnly = true)
    public void enforceServiceLimit(Long empresaId, long currentCount) {
        Suscripcion sub = subscriptionService.getSuscripcionEntityByEmpresa(empresaId);
        if (sub == null) return;
        Plan plan = sub.getPlan();
        Integer limit = plan.getLimiteServicios();
        if (limit != null && limit > 0 && currentCount >= limit) {
            throw new BusinessException(
                    "Has alcanzado el límite de " + limit + " servicio(s) de tu plan " +
                    plan.getNombre() + ". Actualiza tu plan para agregar más.");
        }
    }

    @Transactional(readOnly = true)
    public void enforceReminderLimit(Long usuarioId, long currentCount, String role) {
        Suscripcion sub = subscriptionService.getSuscripcionByUsuarioId(usuarioId);
        if (sub == null) return;
        Integer limit = sub.getPlan().getLimiteRecordatorios();
        if (limit != null && limit > 0 && currentCount >= limit) {
            throw new BusinessException(
                    "Has alcanzado el límite de " + limit + " recordatorio(s) de tu plan " +
                    sub.getPlan().getNombre() + ". Actualiza tu plan para crear más.");
        }
    }

    @Transactional(readOnly = true)
    public void enforceIaLimit(Long usuarioId, long currentUsage) {
        Suscripcion sub = subscriptionService.getSuscripcionByUsuarioId(usuarioId);
        if (sub == null) return;
        Integer limit = sub.getPlan().getLimiteIaUso();
        if (limit != null && limit > 0 && currentUsage >= limit) {
            throw new BusinessException(
                    "Has alcanzado el límite de " + limit + " consulta(s) de IA de tu plan " +
                    sub.getPlan().getNombre() + ". Actualiza tu plan para continuar.");
        }
    }

    @Transactional(readOnly = true)
    public Plan getUserPlan(Long usuarioId) {
        Suscripcion sub = subscriptionService.getSuscripcionByUsuarioId(usuarioId);
        return sub != null ? sub.getPlan() : null;
    }
}
