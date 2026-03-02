package com.vet_saas.modules.subscription.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.subscription.dto.SubscriptionUsageDto;
import com.vet_saas.modules.subscription.model.Plan;
import com.vet_saas.modules.subscription.model.Suscripcion;
import com.vet_saas.modules.subscription.service.SubscriptionService;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<Plan>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.getAvailablePlans(),
                "Planes de suscripción recuperados exitosamente"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<ApiResponse<Suscripcion>> getMySubscription(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.getSuscripcionActual(usuario),
                "Suscripción actual recuperada exitosamente"));
    }

    @PatchMapping("/update-plan")
    public ResponseEntity<ApiResponse<Suscripcion>> updatePlan(
            @RequestParam Long empresaId,
            @RequestParam Long planId) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.updatePlan(empresaId, planId),
                "Plan de suscripción actualizado con éxito"));
    }

    @GetMapping("/usage/me")
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<ApiResponse<SubscriptionUsageDto>> getUsage(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.getUsageMetrics(usuario),
                "Métricas de uso recuperadas exitosamente"));
    }

}
