package com.vet_saas.modules.medical_record.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.medical_record.dto.CreateHistoriaClinicaDto;
import com.vet_saas.modules.medical_record.dto.HistoriaClinicaResponse;
import com.vet_saas.modules.medical_record.service.HistoriaClinicaService;
import com.vet_saas.modules.user.model.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medical-records")
@RequiredArgsConstructor
public class HistoriaClinicaController {

    private final HistoriaClinicaService medicalRecordService;

    @PostMapping
    @PreAuthorize("hasRole('VETERINARIO')")
    public ResponseEntity<ApiResponse<HistoriaClinicaResponse>> createEntry(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid CreateHistoriaClinicaDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        medicalRecordService.createEntry(usuario.getId(), dto),
                        "Entrada de historia clínica registrada exitosamente"));
    }

    @GetMapping("/pet/{mascotaId}")
    public ResponseEntity<ApiResponse<List<HistoriaClinicaResponse>>> getByPet(@PathVariable Long mascotaId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        medicalRecordService.getHistoryByMascota(mascotaId),
                        "Historia clínica recuperada"));
    }
}
