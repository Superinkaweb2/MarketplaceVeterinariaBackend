package com.vet_saas.modules.sales.service;

import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.core.exceptions.types.ResourceNotFoundException;
import com.vet_saas.modules.catalog.model.Producto;
import com.vet_saas.modules.catalog.model.Servicio;
import com.vet_saas.modules.catalog.repository.ProductoRepository;
import com.vet_saas.modules.catalog.repository.ServicioRepository;
import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.company.repository.EmpresaRepository;
import com.vet_saas.modules.sales.dto.CreateOrderDto;
import com.vet_saas.modules.sales.dto.OrderItemDto;
import com.vet_saas.modules.sales.model.DetalleOrden;
import com.vet_saas.modules.sales.model.EstadoOrden;
import com.vet_saas.modules.sales.dto.OrderResponseDto;
import com.vet_saas.modules.sales.model.Orden;
import com.vet_saas.modules.sales.repository.OrdenRepository;
import com.vet_saas.modules.user.model.Role;
import com.vet_saas.modules.user.model.Usuario;
import com.vet_saas.modules.user.repository.UsuarioRepository;
import com.vet_saas.modules.veterinarian.model.Veterinario;
import com.vet_saas.modules.veterinarian.repository.VeterinarioRepository;
import lombok.RequiredArgsConstructor;
import com.vet_saas.modules.points.service.PointsService;
import com.vet_saas.modules.points.service.RewardService;
import com.vet_saas.modules.client.repository.ClienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrdenRepository ordenRepository;
    private final ProductoRepository productoRepository;
    private final ServicioRepository servicioRepository;
    private final EmpresaRepository empresaRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PointsService pointsService;
    private final ClienteRepository clienteRepository;
    private final RewardService rewardService;

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getMyOrdersFiltered(Usuario usuario, EstadoOrden estado, String codigoOrden, String startDate, String endDate, Pageable pageable) {
        Specification<Orden> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filtro por rol (Empresa o Cliente)
            if (usuario.getRol() == Role.EMPRESA) {
                Empresa empresa = empresaRepository.findByUsuarioPropietarioId(usuario.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada"));
                predicates.add(cb.equal(root.get("empresa").get("id"), empresa.getId()));
            } else {
                predicates.add(cb.equal(root.get("usuarioCliente").get("id"), usuario.getId()));
            }

            // Filtro por estado
            if (estado != null) {
                predicates.add(cb.equal(root.get("estado"), estado));
            }

            // Filtro por código de orden
            if (codigoOrden != null && !codigoOrden.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("codigoOrden")), "%" + codigoOrden.toLowerCase() + "%"));
            }

            // Filtro por rango de fechas
            if (startDate != null && !startDate.isBlank()) {
                try {
                    LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
                } catch (Exception e) {
                    // Ignorar error de parseo
                }
            }
            if (endDate != null && !endDate.isBlank()) {
                try {
                    LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
                } catch (Exception e) {
                    // Ignorar error de parseo
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ordenRepository.findAll(spec, pageable).map(OrderResponseDto::fromEntity);
    }

    @Transactional
    public Long createOrder(CreateOrderDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByCorreo(email).orElseThrow();

        Empresa empresa = null;
        Veterinario veterinario = null;

        if (dto.empresaId() != null) {
            empresa = empresaRepository.findById(dto.empresaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Empresa", "id", dto.empresaId()));
        } else if (dto.veterinarioId() != null) {
            veterinario = veterinarioRepository.findById(dto.veterinarioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Veterinario", "id", dto.veterinarioId()));
        } else {
            throw new BusinessException("Debe indicar una Empresa o un Veterinario para la orden");
        }

        String codigo = "ORD-" + System.currentTimeMillis();

        // Procesar dirección de envío
        HashMap<String, Object> direccionEnvio = new HashMap<>();
        if (dto.destinoDireccion() != null && !dto.destinoDireccion().isEmpty()) {
            direccionEnvio.put("lat", dto.destinoLat());
            direccionEnvio.put("lng", dto.destinoLng());
            direccionEnvio.put("direccion", dto.destinoDireccion());
            if (dto.destinoReferencia() != null) {
                direccionEnvio.put("referencia", dto.destinoReferencia());
            }
        }

        Orden orden = Orden.builder()
                .codigoOrden(codigo)
                .usuarioCliente(usuario)
                .empresa(empresa)
                .veterinario(veterinario)
                .estado(EstadoOrden.PENDIENTE)
                .detalles(new ArrayList<>())
                .direccionEnvio(direccionEnvio)
                .metodoPago("POR_DEFINIR") // Valor temporal hasta integrar pasarela
                .build();

        BigDecimal subtotalGeneral = BigDecimal.ZERO;

        for (OrderItemDto itemDto : dto.items()) {
            BigDecimal precio;
            Producto producto = null;
            Servicio servicio = null;

            if (itemDto.productoId() != null) {
                producto = productoRepository.findById(itemDto.productoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Producto", "id", itemDto.productoId()));

                if (empresa != null
                        && (producto.getEmpresa() == null || !producto.getEmpresa().getId().equals(empresa.getId()))) {
                    throw new BusinessException(
                            "El producto " + producto.getNombre() + " no pertenece a la veterinaria seleccionada");
                }
                if (producto.getStock() < itemDto.cantidad()) {
                    throw new BusinessException("Stock insuficiente para: " + producto.getNombre());
                }
                precio = producto.getPrecioActual();
                producto.setStock(producto.getStock() - itemDto.cantidad());
            } else if (itemDto.servicioId() != null) {
                servicio = servicioRepository.findById(itemDto.servicioId())
                        .orElseThrow(() -> new ResourceNotFoundException("Servicio", "id", itemDto.servicioId()));

                // Validar que el servicio pertenezca al vendor seleccionado
                boolean pertenece = false;
                if (empresa != null && servicio.getEmpresa() != null
                        && servicio.getEmpresa().getId().equals(empresa.getId())) {
                    pertenece = true;
                } else if (veterinario != null && servicio.getVeterinario() != null
                        && servicio.getVeterinario().getId().equals(veterinario.getId())) {
                    pertenece = true;
                }

                if (!pertenece) {
                    throw new BusinessException("El servicio no pertenece a esta veterinaria");
                }
                precio = servicio.getPrecio();
            } else {
                throw new BusinessException("Cada item debe tener un producto o un servicio");
            }

            BigDecimal subtotalItem = precio.multiply(BigDecimal.valueOf(itemDto.cantidad()));
            subtotalGeneral = subtotalGeneral.add(subtotalItem);

            DetalleOrden detalle = DetalleOrden.builder()
                    .orden(orden)
                    .producto(producto)
                    .servicio(servicio)
                    .cantidad(itemDto.cantidad())
                    .precioUnitario(precio)
                    .subtotal(subtotalItem)
                    .build();

            orden.getDetalles().add(detalle);
        }

        orden.setSubtotal(subtotalGeneral);
        
        // Si hay costo de envío se asigna, si es null entonces es 0 (retiro en tienda)
        BigDecimal costoEnvio = dto.costoEnvio() != null ? dto.costoEnvio() : BigDecimal.ZERO;
        orden.setCostoEnvio(costoEnvio);
        
        orden.setComisionPlataforma(subtotalGeneral.multiply(new BigDecimal("0.05"))); // 5% Comision

        // ===== APLICAR DESCUENTO DE RECOMPENSA CANJEADA =====
        BigDecimal descuentoTotal = BigDecimal.ZERO;
        if (dto.canjeRecompensaId() != null) {
            try {
                com.vet_saas.modules.points.model.CanjeRecompensa canje = 
                    rewardService.getCanjeById(dto.canjeRecompensaId());
                
                if (canje.getUtilizado()) {
                    throw new BusinessException("Esta recompensa ya fue utilizada");
                }
                
                com.vet_saas.modules.points.model.Recompensa recompensa = canje.getRecompensa();
                
                // Calcular el descuento basado en el tipo
                if ("PORCENTAJE".equals(recompensa.getTipoDescuento())) {
                    if (Boolean.TRUE.equals(recompensa.getAplicaACiertosProductos()) && recompensa.getProductos() != null) {
                        // Aplicar solo a productos elegibles
                        for (DetalleOrden detalle : orden.getDetalles()) {
                            if (detalle.getProducto() != null && recompensa.getProductos().stream()
                                     .anyMatch(p -> p.getId().equals(detalle.getProducto().getId()))) {
                                BigDecimal descItem = detalle.getSubtotal()
                                    .multiply(recompensa.getValorDescuento())
                                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                                descuentoTotal = descuentoTotal.add(descItem);
                            }
                        }
                    } else {
                        // Aplicar a toda la orden
                        descuentoTotal = subtotalGeneral
                             .multiply(recompensa.getValorDescuento())
                             .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                    }
                } else if ("MONTO_FIJO".equals(recompensa.getTipoDescuento())) {
                    descuentoTotal = recompensa.getValorDescuento();
                }
                
                // No permitir descuento mayor al subtotal
                if (descuentoTotal.compareTo(subtotalGeneral) > 0) {
                    descuentoTotal = subtotalGeneral;
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                System.err.println("Error applying reward discount: " + e.getMessage());
            }
        }
        
        orden.setDescuento(descuentoTotal);
        orden.setTotal(subtotalGeneral.add(costoEnvio).subtract(descuentoTotal));

        ordenRepository.save(orden);
        
        // Mark reward as used after saving order
        if (dto.canjeRecompensaId() != null && descuentoTotal.compareTo(BigDecimal.ZERO) > 0) {
            try {
                rewardService.markRewardAsUsed(dto.canjeRecompensaId(), orden.getId());
            } catch (Exception e) {
                System.err.println("Error marking reward as used: " + e.getMessage());
            }
        }
        
        // Gamification logic (Earning Points for Purchases)
        if (usuario.getRol() == Role.CLIENTE) {
             try {
                 clienteRepository.findByUsuarioId(usuario.getId()).ifPresent(perfil -> {
                      // Check if it's the first order
                      long ordersCount = ordenRepository.findByUsuarioClienteId(usuario.getId(), Pageable.unpaged()).getTotalElements();
                      if (ordersCount == 1) { // 1 means this is the first one saved
                          pointsService.addPoints(perfil.getId(), "PRIMERA_COMPRA", orden.getId(), "¡Felicidades por tu primera compra!");
                      } else {
                          pointsService.addPoints(perfil.getId(), "COMPRA", orden.getId(), "Puntos por compra #" + orden.getCodigoOrden());
                      }
                 });
             } catch (Exception e) {
                 System.err.println("Error adding points for purchase: " + e.getMessage());
             }
        }

        return orden.getId();
    }
}