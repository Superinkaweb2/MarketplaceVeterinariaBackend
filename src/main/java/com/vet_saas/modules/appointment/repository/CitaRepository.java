package com.vet_saas.modules.appointment.repository;

import com.vet_saas.modules.appointment.model.Cita;
import com.vet_saas.modules.pet.model.Mascota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CitaRepository extends JpaRepository<Cita, Long> {
    List<Cita> findByEmpresaId(Long empresaId);

    List<Cita> findByVeterinarioId(Long veterinarioId);

    List<Cita> findByClienteId(Long clienteId);

    List<Cita> findByEmpresaIdAndFechaProgramada(Long empresaId, LocalDate fecha);

    List<Cita> findByVeterinarioIdAndFechaProgramada(Long veterinarioId, LocalDate fecha);

    @Query("SELECT DISTINCT c.mascota FROM Cita c WHERE c.veterinario.id = :veterinarioId AND c.mascota IS NOT NULL")
    List<Mascota> findUniquePatientsByVeterinarioId(@Param("veterinarioId") Long veterinarioId);
}
