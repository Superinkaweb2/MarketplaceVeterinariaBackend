package com.vet_saas.modules.sales.repository;

import com.vet_saas.modules.sales.model.Orden;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import com.vet_saas.modules.sales.model.EstadoOrden;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, Long> {

        Page<Orden> findByUsuarioClienteId(Long usuarioId, Pageable pageable);

        @Query("SELECT o FROM Orden o JOIN FETCH o.detalles d JOIN FETCH d.producto WHERE o.id = :id")
        Optional<Orden> findByIdWithDetails(@Param("id") Long id);

        Optional<Orden> findByCodigoOrden(String codigoOrden);

        @Query("SELECT o FROM Orden o " +
                        "JOIN FETCH o.usuarioCliente " +
                        "JOIN FETCH o.empresa " +
                        "JOIN FETCH o.detalles d " +
                        "JOIN FETCH d.producto " +
                        "WHERE o.id = :id")
        Optional<Orden> findByIdForEmail(@Param("id") Long id);

        @Query("SELECT COALESCE(SUM(o.total), 0) " +
                        "FROM Orden o " +
                        "WHERE o.empresa.id = :empresaId " +
                        "AND o.estado = :estado " +
                        "AND o.createdAt >= :fechaInicio")
        BigDecimal sumTotalByEmpresaAndEstadoAndFechaGte(
                        @Param("empresaId") Long empresaId,
                        @Param("estado") EstadoOrden estado,
                        @Param("fechaInicio") LocalDateTime fechaInicio);

        Long countByEmpresaIdAndEstado(Long empresaId, EstadoOrden estado);

        @Query("SELECT COUNT(o) " +
                        "FROM Orden o " +
                        "WHERE o.empresa.id = :empresaId " +
                        "AND o.estado = :estado " +
                        "AND o.createdAt >= :inicioDia")
        Long countByEmpresaEstadoAndFechaGte(
                        @Param("empresaId") Long empresaId,
                        @Param("estado") EstadoOrden estado,
                        @Param("inicioDia") LocalDateTime inicioDia);

        @Query("SELECT COUNT(DISTINCT o.usuarioCliente.id) " +
                        "FROM Orden o " +
                        "WHERE o.empresa.id = :empresaId " +
                        "AND o.estado = 'PAGADO'")
        Long countDistinctClientesByEmpresa(@Param("empresaId") Long empresaId);
}
