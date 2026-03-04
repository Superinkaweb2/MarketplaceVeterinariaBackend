package com.vet_saas.modules.appointment.dto;

import com.vet_saas.modules.appointment.model.AppointmentStatus;
import com.vet_saas.modules.appointment.model.Cita;
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
public class CitaResponse {
    private Long id;
    private String clienteNombre;
    private String mascotaNombre;
    private String servicioNombre;
    private String veterinarioNombre;
    private LocalDate fechaProgramada;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private AppointmentStatus estado;
    private String notasCliente;
    private String notasInternas;

    public static CitaResponse fromEntity(Cita cita) {
        return CitaResponse.builder()
                .id(cita.getId())
                .clienteNombre(cita.getCliente().getCorreo()) // Simplified for now
                .mascotaNombre(cita.getMascota() != null ? cita.getMascota().getNombre() : "N/A")
                .servicioNombre(cita.getServicio().getNombre())
                .veterinarioNombre(cita.getVeterinario() != null
                        ? cita.getVeterinario().getNombres() + " " + cita.getVeterinario().getApellidos()
                        : "Pendiente")
                .fechaProgramada(cita.getFechaProgramada())
                .horaInicio(cita.getHoraInicio())
                .horaFin(cita.getHoraFin())
                .estado(cita.getEstado())
                .notasCliente(cita.getNotasCliente())
                .notasInternas(cita.getNotasInternas())
                .build();
    }
}
