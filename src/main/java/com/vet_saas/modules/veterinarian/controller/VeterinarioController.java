package com.vet_saas.modules.veterinarian.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.user.model.Usuario;
import com.vet_saas.modules.veterinarian.dto.VeterinarioRequest;
import com.vet_saas.modules.veterinarian.dto.VeterinarioResponse;
import com.vet_saas.modules.veterinarian.service.VeterinarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/veterinarians")
@RequiredArgsConstructor
public class VeterinarioController {

    private final VeterinarioService veterinarioService;

    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<VeterinarioResponse>> createProfile(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid VeterinarioRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        veterinarioService.createProfile(usuario.getId(), request),
                        "Perfil de veterinario creado correctamente"
                )
        );
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<VeterinarioResponse>> getMyProfile(
            @AuthenticationPrincipal Usuario usuario
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        veterinarioService.getProfileByUserId(usuario.getId()),
                        "Perfil recuperado"
                )
        );
    }
}