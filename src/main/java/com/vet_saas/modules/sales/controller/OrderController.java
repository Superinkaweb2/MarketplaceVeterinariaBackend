package com.vet_saas.modules.sales.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.sales.dto.CreateOrderDto;
import com.vet_saas.modules.sales.dto.OrderResponseDto;
import com.vet_saas.modules.sales.service.OrderService;
import com.vet_saas.modules.user.model.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createOrder(@RequestBody @Valid CreateOrderDto dto) {
        Long pedidoId = orderService.createOrder(dto);
        return ResponseEntity.ok(
                ApiResponse.success(pedidoId, "Orden creada exitosamente. Pendiente de pago."));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<OrderResponseDto>>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal Usuario usuario,
            Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(orderService.getMyOrders(usuario, pageable),
                        "Tus órdenes recuperadas exitosamente"));
    }
}