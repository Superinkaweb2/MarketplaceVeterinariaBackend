package com.vet_saas.modules.auth.service;

import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.modules.auth.dto.AuthResponse;
import com.vet_saas.modules.auth.dto.ChangePasswordRequest;
import com.vet_saas.modules.auth.dto.LoginRequest;
import com.vet_saas.modules.auth.dto.RegisterRequest;
import com.vet_saas.modules.notification.service.EmailService;
import com.vet_saas.modules.user.model.Usuario;
import com.vet_saas.modules.user.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final AuthTokenService authTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (usuarioRepository.findByCorreo(request.correo()).isPresent()) {
            throw new BusinessException("El correo ya está registrado");
        }

        Usuario user = Usuario.builder()
                .correo(request.correo())
                .password(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .estado(true)
                .emailVerificado(false)
                .build();

        usuarioRepository.save(user);

        // Generar token de verificación
        String token = authTokenService.createToken(user, com.vet_saas.modules.auth.model.TokenType.EMAIL_VERIFICATION,
                24);

        // Enviar correo de verificación
        emailService.sendVerificationEmail(user, token);

        // Generar tokens usando RefreshTokenService (opaco + JWT)
        return refreshTokenService.createRefreshToken(user.getId());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.correo(),
                        request.password()));

        Usuario user = usuarioRepository.findByCorreo(request.correo())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        // Generar tokens usando RefreshTokenService (revoca previos + crea nuevos)
        return refreshTokenService.createRefreshToken(user.getId());
    }

    @Transactional
    public void verifyEmail(String tokenValue) {
        com.vet_saas.modules.auth.model.AuthToken token = authTokenService.validateToken(tokenValue,
                com.vet_saas.modules.auth.model.TokenType.EMAIL_VERIFICATION);
        Usuario user = token.getUsuario();
        user.setEmailVerificado(true);
        usuarioRepository.save(user);
        authTokenService.deleteToken(token);
    }

    @Transactional
    public void forgotPassword(String correo) {
        Usuario user = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new BusinessException("No existe un usuario con ese correo"));

        String token = authTokenService.createToken(user, com.vet_saas.modules.auth.model.TokenType.PASSWORD_RESET, 1);
        emailService.sendPasswordResetEmail(user, token);
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        com.vet_saas.modules.auth.model.AuthToken token = authTokenService.validateToken(tokenValue,
                com.vet_saas.modules.auth.model.TokenType.PASSWORD_RESET);
        Usuario user = token.getUsuario();

        user.setPassword(passwordEncoder.encode(newPassword));
        usuarioRepository.save(user);

        authTokenService.deleteToken(token);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        Usuario user = usuarioRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException("La contraseña actual es incorrecta");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException("Las contraseñas no coinciden");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        usuarioRepository.save(user);
    }
}
