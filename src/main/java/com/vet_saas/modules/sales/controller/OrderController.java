package com.vet_saas.modules.sales.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.sales.dto.CreateOrderDto;
import com.vet_saas.modules.sales.model.Orden;
import com.vet_saas.modules.sales.service.OrderService;
import com.vet_saas.modules.user.model.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<ApiResponse<Page<Orden>>> getMyOrders(
            @AuthenticationPrincipal Usuario usuario,
            Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(orderService.getMyOrders(usuario, pageable),
                        "Tus órdenes recuperadas exitosamente"));
    }
}