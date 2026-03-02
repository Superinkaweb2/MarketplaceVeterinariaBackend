package com.vet_saas.modules.dashboard.service;

import com.vet_saas.modules.dashboard.dto.DashboardMetricsDto;
import com.vet_saas.modules.dashboard.dto.TopProductoDto;
import com.vet_saas.modules.sales.model.EstadoOrden;
import com.vet_saas.modules.sales.repository.DetalleOrdenRepository;
import com.vet_saas.modules.sales.repository.OrdenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrdenRepository ordenRepository;
    private final DetalleOrdenRepository detalleOrdenRepository;

    @Transactional(readOnly = true)
    public DashboardMetricsDto getMetrics(Long empresaId) {

        // Ventanas de Tiempo Funcionales
        LocalDateTime inicioDia = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime inicioMes = LocalDateTime.now().minusDays(30);

        // Consultas de Alta Velocidad
        BigDecimal totalVentasMes = ordenRepository.sumTotalByEmpresaAndEstadoAndFechaGte(
                empresaId, EstadoOrden.PAGADO, inicioMes);

        Long ordenesPendientes = ordenRepository.countByEmpresaIdAndEstado(
                empresaId, EstadoOrden.PENDIENTE);

        Long ordenesPagadasHoy = ordenRepository.countByEmpresaEstadoAndFechaGte(
                empresaId, EstadoOrden.PAGADO, inicioDia);

        Long clientesActivos = ordenRepository.countDistinctClientesByEmpresa(empresaId);

        List<TopProductoDto> topProductos = detalleOrdenRepository.findTopProductos(
                empresaId, PageRequest.of(0, 5));

        return DashboardMetricsDto.builder()
                .totalVentasMes(totalVentasMes)
                .ordenesPendientes(ordenesPendientes)
                .ordenesPagadasHoy(ordenesPagadasHoy)
                .clientesActivos(clientesActivos)
                .topProductos(topProductos)
                .build();
    }
}
