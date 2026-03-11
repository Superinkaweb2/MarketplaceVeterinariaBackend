package com.vet_saas.modules.sales.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CreateOrderDto(
                Long empresaId,
                Long veterinarioId,
                
                // Campos de Delivery opcionales
                BigDecimal costoEnvio,
                BigDecimal destinoLat,
                BigDecimal destinoLng,
                String destinoDireccion,
                String destinoReferencia,

                @NotEmpty(message = "El carrito no puede estar vacío") List<OrderItemDto> items) {
}