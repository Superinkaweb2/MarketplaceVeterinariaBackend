package com.vet_saas.modules.client.repository;

import com.vet_saas.modules.client.model.PerfilCliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<PerfilCliente, Long> {

        Optional<PerfilCliente> findByUsuarioId(Long usuarioId);

        boolean existsByUsuarioId(Long usuarioId);

        // Buscar clientes por nombre o apellido (insensible a mayúsculas)
        @Query("SELECT c FROM PerfilCliente c WHERE " +
                        "LOWER(c.nombres) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                        "LOWER(c.apellidos) LIKE LOWER(CONCAT('%', :q, '%'))")
        Page<PerfilCliente> searchByNombre(@Param("q") String q, Pageable pageable);

        // Clientes de una empresa específica (los que tienen órdenes con esa empresa)
        @Query("SELECT DISTINCT c FROM PerfilCliente c " +
                        "JOIN c.usuario u " +
                        "JOIN com.vet_saas.modules.sales.model.Orden o ON o.usuarioCliente.id = u.id " +
                        "WHERE o.empresa.id = :empresaId " +
                        "AND (CAST(:q AS string) IS NULL OR LOWER(c.nombres) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) "
                        +
                        "     OR LOWER(c.apellidos) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))")
        Page<PerfilCliente> findClientesByEmpresaId(
                        @Param("empresaId") Long empresaId,
                        @Param("q") String q,
                        Pageable pageable);

}
