package com.vet_saas.modules.complaint.controller;

import com.vet_saas.modules.complaint.dto.ReclamoRequestDto;
import com.vet_saas.modules.complaint.model.Reclamo;
import com.vet_saas.modules.complaint.service.ReclamoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reclamos")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ReclamoController {

    private final ReclamoService reclamoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> registrarReclamo(
            @RequestPart("reclamo") @Valid ReclamoRequestDto reclamoDto,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {

        // Modificado para recibir la entidad creada y extraer su URL para el Frontend
        Reclamo reclamoGuardado = reclamoService.registrarReclamo(reclamoDto, archivo);

        return ResponseEntity.ok(Map.of("url", reclamoGuardado.getPdfReclamoUrl()));
    }
}
