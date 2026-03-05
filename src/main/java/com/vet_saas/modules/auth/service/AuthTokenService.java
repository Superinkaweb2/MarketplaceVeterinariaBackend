package com.vet_saas.modules.auth.service;

import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.modules.auth.model.AuthToken;
import com.vet_saas.modules.auth.model.TokenType;
import com.vet_saas.modules.auth.repository.AuthTokenRepository;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final AuthTokenRepository authTokenRepository;

    @Transactional
    public String createToken(Usuario usuario, TokenType tipo, int expirationHours) {
        // Eliminar tokens previos del mismo tipo para este usuario
        authTokenRepository.deleteByUsuarioAndTipo(usuario, tipo);

        String tokenValue = UUID.randomUUID().toString();
        AuthToken token = AuthToken.builder()
                .usuario(usuario)
                .token(tokenValue)
                .tipo(tipo)
                .fechaExpiracion(LocalDateTime.now().plusHours(expirationHours))
                .build();

        authTokenRepository.save(token);
        return tokenValue;
    }

    public AuthToken validateToken(String tokenValue, TokenType tipo) {
        AuthToken token = authTokenRepository.findByTokenAndTipo(tokenValue, tipo)
                .orElseThrow(() -> new BusinessException("Token inválido o no encontrado"));

        if (token.isExpired()) {
            authTokenRepository.delete(token);
            throw new BusinessException("El token ha expirado");
        }

        return token;
    }

    @Transactional
    public void deleteToken(AuthToken token) {
        authTokenRepository.delete(token);
    }
}
