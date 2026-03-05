package com.vet_saas.modules.auth.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.auth.dto.*;
import com.vet_saas.modules.auth.service.AuthService;
import com.vet_saas.modules.auth.service.RefreshTokenService;
import com.vet_saas.modules.user.model.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(authService.register(request), "Usuario registrado exitosamente"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(authService.login(request), "Inicio de sesión exitoso"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(refreshTokenService.refreshAccessToken(request.refreshToken()),
                        "Token renovado exitosamente"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody @Valid LogoutRequest request) {
        refreshTokenService.revokeRefreshToken(request.refreshToken());
        return ResponseEntity.ok(
                ApiResponse.success("Sesión cerrada exitosamente"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@AuthenticationPrincipal Usuario usuario) {
        refreshTokenService.revokeAllByUser(usuario.getId());
        return ResponseEntity.ok(
                ApiResponse.success("Todas las sesiones han sido cerradas"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(usuario.getId(), request);
        return ResponseEntity.ok(
                ApiResponse.success("Contraseña actualizada exitosamente"));
    }

    @org.springframework.web.bind.annotation.GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @org.springframework.web.bind.annotation.RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(
                ApiResponse.success("Correo verificado exitosamente"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        authService.forgotPassword(request.correo());
        return ResponseEntity.ok(
                ApiResponse.success("Se ha enviado un correo para restablecer tu contraseña"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(
                ApiResponse.success("Tu contraseña ha sido restablecida exitosamente"));
    }
}
