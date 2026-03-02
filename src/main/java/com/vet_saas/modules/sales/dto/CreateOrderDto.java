package com.vet_saas.modules.sales.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderDto(
        @NotNull(message = "Debes indicar a qué empresa le compras")
        Long empresaId,

        @NotEmpty(message = "El carrito no puede estar vacío")
        List<OrderItemDto> items
) {}