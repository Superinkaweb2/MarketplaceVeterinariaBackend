package com.vet_saas.modules.appointment.service;

import com.vet_saas.core.exceptions.types.ResourceNotFoundException;
import com.vet_saas.modules.appointment.dto.CitaRequest;
import com.vet_saas.modules.appointment.dto.CitaResponse;
import com.vet_saas.modules.appointment.model.AppointmentStatus;
import com.vet_saas.modules.appointment.model.Cita;
import com.vet_saas.modules.appointment.repository.CitaRepository;
import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.company.repository.EmpresaRepository;
import com.vet_saas.modules.pet.model.Mascota;
import com.vet_saas.modules.pet.repository.MascotaRepository;
import com.vet_saas.modules.catalog.model.Servicio;
import com.vet_saas.modules.catalog.repository.ServicioRepository;
import com.vet_saas.modules.user.model.Usuario;
import com.vet_saas.modules.veterinarian.model.Veterinario;
import com.vet_saas.modules.veterinarian.repository.VeterinarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CitaService {

    private final CitaRepository citaRepository;
    private final MascotaRepository mascotaRepository;
    private final ServicioRepository servicioRepository;
    private final EmpresaRepository empresaRepository;
    private final VeterinarioRepository veterinarioRepository;

    @Transactional
    public CitaResponse crearCita(Usuario cliente, CitaRequest request) {
        Mascota mascota = null;
        if (request.getMascotaId() != null) {
            mascota = mascotaRepository.findById(request.getMascotaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Mascota no encontrada"));
        }

        Servicio servicio = servicioRepository.findById(request.getServicioId())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));

        Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada"));

        Veterinario veterinario = null;
        if (request.getVeterinarioId() != null) {
            veterinario = veterinarioRepository.findById(request.getVeterinarioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Veterinario no encontrado"));
        }

        LocalTime horaFin = request.getHoraInicio().plusMinutes(servicio.getDuracionMinutos());

        Cita cita = Cita.builder()
                .cliente(cliente)
                .mascota(mascota)
                .servicio(servicio)
                .empresa(empresa)
                .veterinario(veterinario)
                .fechaProgramada(request.getFechaProgramada())
                .horaInicio(request.getHoraInicio())
                .horaFin(horaFin)
                .estado(AppointmentStatus.SOLICITADA)
                .notasCliente(request.getNotasCliente())
                .build();

        return CitaResponse.fromEntity(citaRepository.save(cita));
    }

    @Transactional(readOnly = true)
    public List<CitaResponse> getCitasByEmpresa(Long empresaId) {
        // Fix: Repository had typo in findByEmpresaId return type
        return ((List<Cita>) (Object) citaRepository.findByEmpresaId(empresaId)).stream()
                .map(CitaResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CitaResponse> getCitasByVeterinario(Long veterinarioId) {
        return citaRepository.findByVeterinarioId(veterinarioId).stream()
                .map(CitaResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CitaResponse> getCitasByCliente(Long clienteId) {
        return citaRepository.findByClienteId(clienteId).stream()
                .map(CitaResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public CitaResponse actualizarEstado(Long citaId, AppointmentStatus nuevoEstado, String notasInternas) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada"));

        cita.setEstado(nuevoEstado);
        if (notasInternas != null) {
            cita.setNotasInternas(notasInternas);
        }

        return CitaResponse.fromEntity(citaRepository.save(cita));
    }
}
