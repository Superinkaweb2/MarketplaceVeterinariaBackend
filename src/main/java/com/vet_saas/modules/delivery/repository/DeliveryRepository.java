package com.vet_saas.modules.delivery.repository;

import com.vet_saas.modules.delivery.model.Delivery;
import com.vet_saas.modules.delivery.model.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    List<Delivery> findByEstado(DeliveryStatus estado);

    @Query("SELECT d FROM Delivery d WHERE d.orden.id = :ordenId")
    Optional<Delivery> findByOrdenId(@Param("ordenId") Long ordenId);

    Optional<Delivery> findByRepartidorIdRepartidorAndEstadoNotIn(Long repartidorId, List<DeliveryStatus> estadosExcluidos);

    /**
     * Para el scheduler: deliveries buscando repartidor por mas de X minutos
     */
    @Query("""
        SELECT d FROM Delivery d
        WHERE d.estado = 'BUSCANDO_REPARTIDOR'
          AND d.createdAt < :hace
        ORDER BY d.createdAt ASC
        """)
    List<Delivery> findBusquedaExpirada(@Param("hace") Instant hace);

    /**
     * Dashboard empresa: todos sus deliveries activos
     */
    @Query("""
    SELECT d FROM Delivery d
    JOIN d.orden o
    WHERE o.empresa.id = :empresaId
      AND d.estado NOT IN :estadosExcluidos
    ORDER BY d.createdAt DESC
    """)
    List<Delivery> findActivosByEmpresa(
            @Param("empresaId") Long empresaId,
            @Param("estadosExcluidos") List<DeliveryStatus> estadosExcluidos
    );

    /**
     * Historial de un repartidor
     */
    List<Delivery> findByRepartidorIdRepartidorOrderByCreatedAtDesc(Long repartidorId);

    @Query("SELECT COUNT(d) > 0 FROM Delivery d WHERE d.orden.id = :ordenId")
    boolean existsByOrdenId(@Param("ordenId") Long ordenId);

    @Query("SELECT COUNT(d) > 0 FROM Delivery d WHERE d.repartidor.idRepartidor = :repartidorId AND d.estado NOT IN :estados")
    boolean existsByRepartidorIdAndEstadoNotIn(
        @Param("repartidorId") Long repartidorId, 
        @Param("estados") List<DeliveryStatus> estados
    );

    @Query("""
    SELECT d FROM Delivery d
    JOIN d.orden o
    WHERE o.empresa.id = :empresaId
      AND d.calificacionProducto IS NOT NULL
    ORDER BY d.entregadoAt DESC
    """)
    List<Delivery> findRatingsByEmpresa(@Param("empresaId") Long empresaId);
}
