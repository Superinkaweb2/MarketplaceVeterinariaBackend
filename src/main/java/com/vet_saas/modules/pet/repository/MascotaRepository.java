package com.vet_saas.modules.pet.repository;

import com.vet_saas.modules.pet.model.Mascota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MascotaRepository extends JpaRepository<Mascota, Long> {

    List<Mascota> findByUsuarioIdAndActivoTrue(Long usuarioId);

    Optional<Mascota> findByIdAndUsuarioIdAndActivoTrue(Long id, Long usuarioId);

    long countByUsuarioIdAndActivoTrue(Long usuarioId);
}
