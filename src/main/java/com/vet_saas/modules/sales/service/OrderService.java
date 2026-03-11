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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrdenRepository ordenRepository;
    private final ProductoRepository productoRepository;
    private final ServicioRepository servicioRepository;
    private final EmpresaRepository empresaRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getMyOrders(Usuario usuario, Pageable pageable) {
        Page<Orden> orders;

        if (usuario.getRol() == Role.EMPRESA) {
            Empresa empresa = empresaRepository.findByUsuarioPropietarioId(usuario.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada"));
            orders = ordenRepository.findByEmpresaId(empresa.getId(), pageable);
        } else {
            orders = ordenRepository.findByUsuarioClienteId(usuario.getId(), pageable);
        }

        return orders.map(OrderResponseDto::fromEntity);
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
        orden.setTotal(subtotalGeneral.add(costoEnvio));

        ordenRepository.save(orden);

        return orden.getId();
    }
}