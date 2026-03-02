package com.vet_saas.modules.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemDto(
        @NotNull
        Long productoId,

        @NotNull
        @Min(1)
        Integer cantidad
) {}