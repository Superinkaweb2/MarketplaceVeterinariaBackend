package com.vet_saas.modules.sales.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderDto(
                Long empresaId,
                Long veterinarioId,

                @NotEmpty(message = "El carrito no puede estar vacío") List<OrderItemDto> items) {
}