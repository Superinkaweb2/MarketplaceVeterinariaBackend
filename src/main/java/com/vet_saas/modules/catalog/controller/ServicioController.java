package com.vet_saas.modules.catalog.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.catalog.dto.CreateServiceDto;
import com.vet_saas.modules.catalog.dto.ServiceResponse;
import com.vet_saas.modules.catalog.dto.UpdateServiceDto;
import com.vet_saas.modules.catalog.service.ServicioService;
import com.vet_saas.modules.user.model.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServicioController {

    private final ServicioService servicioService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ServiceResponse>>> getMarketplaceServices(
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) Long veterinarioId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                servicioService.getMarketplaceServices(empresaId, veterinarioId, pageable),
                "Servicios recuperados"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceResponse>> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                servicioService.getServiceById(id),
                "Servicio recuperado"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPRESA', 'VETERINARIO')")
    public ResponseEntity<ApiResponse<Page<ServiceResponse>>> getMyServices(
            @AuthenticationPrincipal Usuario usuario,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                servicioService.getMyServices(usuario, pageable),
                "Tus servicios recuperados exitosamente"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPRESA', 'VETERINARIO')")
    public ResponseEntity<ApiResponse<ServiceResponse>> createService(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid CreateServiceDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                servicioService.createService(usuario, dto),
                "Servicio creado exitosamente"));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPRESA', 'VETERINARIO')")
    public ResponseEntity<ApiResponse<ServiceResponse>> updateService(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable Long id,
            @RequestBody @Valid UpdateServiceDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                servicioService.updateService(usuario, id, dto),
                "Servicio actualizado exitosamente"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPRESA', 'VETERINARIO')")
    public ResponseEntity<ApiResponse<Void>> deleteService(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable Long id) {
        servicioService.softDeleteService(usuario, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Servicio desactivado correctamente"));
    }
}
