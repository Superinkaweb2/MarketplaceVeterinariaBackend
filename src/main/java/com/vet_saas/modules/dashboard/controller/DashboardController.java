package com.vet_saas.modules.dashboard.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.company.repository.EmpresaRepository;
import com.vet_saas.modules.dashboard.dto.DashboardMetricsDto;
import com.vet_saas.modules.dashboard.service.DashboardService;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final EmpresaRepository empresaRepository;

    @GetMapping
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<ApiResponse<DashboardMetricsDto>> getDashboardMetrics(
            @AuthenticationPrincipal Usuario usuario) {

        Empresa empresa = empresaRepository.findByUsuarioPropietarioId(usuario.getId())
                .orElseThrow(() -> new IllegalStateException("Empresa no encontrada para el propietario actual"));

        DashboardMetricsDto metrics = dashboardService.getMetrics(empresa.getId());

        return ResponseEntity.ok(
                ApiResponse.success(metrics, "Métricas del dashboard recuperadas exitosamente"));
    }
}
