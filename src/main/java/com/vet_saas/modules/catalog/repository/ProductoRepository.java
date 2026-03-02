package com.vet_saas.modules.catalog.repository;

import com.vet_saas.modules.catalog.model.EstadoProducto;
import com.vet_saas.modules.catalog.model.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

        // Helper para verificar existencia de SKU por empresa
        boolean existsByEmpresaIdAndSku(Long empresaId, String sku);

        boolean existsByEmpresaIdAndSkuAndIdNot(Long empresaId, String sku, Long id);

        // Queries de empresa (dashboard)
        Page<Producto> findByEmpresaIdAndActivoTrue(Long empresaId, Pageable pageable);

        Optional<Producto> findByIdAndActivoTrue(Long id);

        Optional<Producto> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

        long countByEmpresaIdAndActivoTrue(Long empresaId);

        // Endpoint individual público
        Optional<Producto> findByIdAndEstadoAndVisibleTrueAndActivoTrue(Long id, EstadoProducto estado);

        // Queries para Marketplace (públicos)
        @Query("SELECT p FROM Producto p " +
                        "JOIN FETCH p.empresa " +
                        "LEFT JOIN FETCH p.categoria " +
                        "WHERE p.estado = :estado AND p.visible = true AND p.activo = true " +
                        "AND (CAST(:categoriaId AS long) IS NULL OR p.categoria.id = :categoriaId) " +
                        "AND (CAST(:q AS string) IS NULL OR :q = '' OR " +
                        "     LOWER(p.nombre) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
                        "     LOWER(p.descripcion) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))")
        Page<Producto> findMarketplaceProducts(
                        @Param("q") String q,
                        @Param("estado") EstadoProducto estado,
                        @Param("categoriaId") Long categoriaId,
                        Pageable pageable);

}