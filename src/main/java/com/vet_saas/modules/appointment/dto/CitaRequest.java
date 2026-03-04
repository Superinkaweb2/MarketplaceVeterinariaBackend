package com.vet_saas.modules.appointment.dto;

import com.vet_saas.modules.appointment.model.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaRequest {
    private Long mascotaId;
    private Long servicioId;
    private Long empresaId;
    private Long veterinarioId;
    private LocalDate fechaProgramada;
    private LocalTime horaInicio;
    private String notasCliente;
}
