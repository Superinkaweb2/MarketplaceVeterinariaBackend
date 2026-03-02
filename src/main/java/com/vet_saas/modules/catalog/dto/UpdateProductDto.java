package com.vet_saas.modules.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateProductDto(
        String nombre,
        String descripcion,
        BigDecimal precio,
        BigDecimal precioOferta,
        LocalDateTime ofertaInicio,
        LocalDateTime ofertaFin,
        Integer stock,
        String sku,
        Long categoriaId,
        Boolean visible,
        String estado) {
}
