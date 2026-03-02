package com.vet_saas.modules.catalog.dto;

import com.vet_saas.modules.catalog.model.ModalidadServicio;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceResponse(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer duracionMinutos,
        ModalidadServicio modalidad,
        Boolean activo,
        Boolean visible,
        Long empresaId,
        String empresaNombre,
        Long veterinarioId,
        String veterinarioNombres,
        String veterinarioApellidos,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
