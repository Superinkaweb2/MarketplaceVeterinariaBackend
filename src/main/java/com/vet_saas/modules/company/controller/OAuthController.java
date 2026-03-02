package com.vet_saas.modules.company.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.company.dto.MercadoPagoOAuthRequest;
import com.vet_saas.modules.company.service.CompanyService;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final CompanyService companyService;

    @PostMapping("/mercadopago/exchange")
    public ResponseEntity<ApiResponse<Void>> connectMercadoPago(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody MercadoPagoOAuthRequest request) {

        companyService.connectMercadoPago(usuario, request.code(), request.redirectUri());

        return ResponseEntity.ok(ApiResponse.success("Cuenta de Mercado Pago conectada exitosamente"));
    }
}
