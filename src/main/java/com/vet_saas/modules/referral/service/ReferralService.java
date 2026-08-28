package com.vet_saas.modules.referral.service;

import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.modules.referral.dto.ReferralCountResponse;
import com.vet_saas.modules.referral.model.Referido;
import com.vet_saas.modules.referral.repository.ReferidoRepository;
import com.vet_saas.modules.user.model.Usuario;
import com.vet_saas.modules.user.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferidoRepository referidoRepository;
    private final UsuarioRepository usuarioRepository;

    private static final long REFERIDOS_PARA_DESBLOQUEO = 10;

    /**
     * Genera un código de referido único para el usuario.
     * El código se deriva del ID del usuario y es determinista.
     * No crea registros en la tabla referidos hasta que alguien use el código.
     */
    public String generateReferralCode(Usuario usuario) {
        return "H360-" + usuario.getId();
    }

    /**
     * Aplica un código de referido. Crea un registro en la tabla referidos
     * vinculando al usuario que refiere con el nuevo usuario.
     */
    @Transactional
    public void applyReferralCode(Usuario nuevoUsuario, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new BusinessException("El código de referido es obligatorio");
        }

        // Validar formato del código
        if (!codigo.startsWith("H360-")) {
            throw new BusinessException("Código de referido inválido");
        }

        // Extraer el ID del usuario que refiere del código
        Long referrerId;
        try {
            referrerId = Long.parseLong(codigo.substring(5));
        } catch (NumberFormatException e) {
            throw new BusinessException("Código de referido inválido");
        }

        // No permitir auto-referencia
        if (referrerId.equals(nuevoUsuario.getId())) {
            throw new BusinessException("No puedes usar tu propio código de referido");
        }

        // Validar que el usuario que refiere exista en la BD
        if (!usuarioRepository.findById(referrerId).isPresent()) {
            throw new BusinessException("El código de referido no corresponde a un usuario válido");
        }

        // Verificar si ya fue referido por alguien (excluyendo auto-referencias)
        boolean alreadyReferred = referidoRepository.existsByUsuarioRefiridoIdAndNotSelfReference(
                nuevoUsuario.getId(), nuevoUsuario.getId());
        if (alreadyReferred) {
            throw new BusinessException("Ya has sido referido por otro usuario");
        }

        // Verificar si ya aplicó este mismo código
        boolean alreadyApplied = referidoRepository
                .existsByUsuarioRefiridoIdAndUsuarioQueRefirioId(nuevoUsuario.getId(), referrerId);
        if (alreadyApplied) {
            throw new BusinessException("Ya has aplicado este código de referido");
        }

        // Buscar el usuario que refiere (managed entity para evitar FK violation)
        Usuario referrer = usuarioRepository.findById(referrerId)
                .orElseThrow(() -> new BusinessException("El código de referido no corresponde a un usuario válido"));

        Referido newReferido = Referido.builder()
                .usuarioQueRefirio(referrer)
                .usuarioRefirido(nuevoUsuario)
                .codigoReferido(codigo)
                .build();

        try {
            referidoRepository.save(newReferido);
            log.info("Usuario {} referido por usuario {}", nuevoUsuario.getId(), referrerId);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Este código de referido ya ha sido utilizado por otro usuario");
        }
    }

    /**
     * Cuenta cuántos usuarios ha referido uno dado (excluyendo auto-referencias).
     */
    @Transactional(readOnly = true)
    public ReferralCountResponse getReferralCount(Long usuarioId) {
        long count = referidoRepository.countByUsuarioQueRefirioId(usuarioId);
        boolean desbloqueado = count >= REFERIDOS_PARA_DESBLOQUEO;

        return ReferralCountResponse.builder()
                .totalReferidos(count)
                .desbloqueo2daMascota(desbloqueado)
                .referidosNecesarios(REFERIDOS_PARA_DESBLOQUEO)
                .referidosRestantes(Math.max(0, REFERIDOS_PARA_DESBLOQUEO - count))
                .build();
    }

    @Transactional(readOnly = true)
    public ReferralCountResponse getReferralCount(Usuario usuario) {
        return getReferralCount(usuario.getId());
    }
}
