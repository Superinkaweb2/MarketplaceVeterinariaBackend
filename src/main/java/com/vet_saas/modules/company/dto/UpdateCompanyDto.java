package com.vet_saas.modules.company.dto;

import jakarta.validation.constraints.Email;

import java.math.BigDecimal;

public record UpdateCompanyDto(
        String nombreComercial,
        String descripcion,
        String tipoServicio,
        String telefono,

        @Email(message = "Formato de email inválido")
        String emailContacto,

        String direccion,
        String ciudad,

        BigDecimal latitud,
        BigDecimal longitud
) {}