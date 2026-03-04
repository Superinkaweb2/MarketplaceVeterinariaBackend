package com.vet_saas.modules.appointment.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.appointment.dto.CitaRequest;
import com.vet_saas.modules.appointment.dto.CitaResponse;
import com.vet_saas.modules.appointment.model.AppointmentStatus;
import com.vet_saas.modules.appointment.service.CitaService;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService citaService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<ApiResponse<CitaResponse>> solicitarCita(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody CitaRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                citaService.crearCita(usuario, request),
                "Cita solicitada exitosamente"));
    }

    @GetMapping("/empresa/{empresaId}")
    @PreAuthorize("hasAnyRole('EMPRESA', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<CitaResponse>>> getCitasEmpresa(@PathVariable Long empresaId) {
        return ResponseEntity.ok(ApiResponse.success(
                citaService.getCitasByEmpresa(empresaId),
                "Citas de la empresa recuperadas"));
    }

    @GetMapping("/veterinario/{veterinarioId}")
    @PreAuthorize("hasAnyRole('VETERINARIO', 'EMPRESA', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<CitaResponse>>> getCitasVeterinario(@PathVariable Long veterinarioId) {
        return ResponseEntity.ok(ApiResponse.success(
                citaService.getCitasByVeterinario(veterinarioId),
                "Citas del veterinario recuperadas"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<ApiResponse<List<CitaResponse>>> getMisCitas(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(ApiResponse.success(
                citaService.getCitasByCliente(usuario.getId()),
                "Mis citas recuperadas"));
    }

    @PatchMapping("/{citaId}/status")
    @PreAuthorize("hasAnyRole('EMPRESA', 'VETERINARIO', 'ADMIN')")
    public ResponseEntity<ApiResponse<CitaResponse>> actualizarEstado(
            @PathVariable Long citaId,
            @RequestParam AppointmentStatus estado,
            @RequestParam(required = false) String notas) {
        return ResponseEntity.ok(ApiResponse.success(
                citaService.actualizarEstado(citaId, estado, notas),
                "Estado de la cita actualizado"));
    }
}
