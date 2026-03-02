package com.vet_saas.modules.veterinarian.service;

import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.modules.user.model.Usuario;
import com.vet_saas.modules.user.repository.UsuarioRepository;
import com.vet_saas.modules.veterinarian.dto.VeterinarioRequest;
import com.vet_saas.modules.veterinarian.dto.VeterinarioResponse;
import com.vet_saas.modules.veterinarian.model.Veterinario;
import com.vet_saas.modules.veterinarian.repository.VeterinarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VeterinarioService {

    private final VeterinarioRepository veterinarioRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public VeterinarioResponse createProfile(Long userId, VeterinarioRequest request) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado"));

        if (veterinarioRepository.findByUsuarioId(userId).isPresent()) {
            throw new BusinessException("El perfil de veterinario ya existe para este usuario");
        }

        if (veterinarioRepository.existsByNumeroColegiatura(request.numeroColegiatura())) {
            throw new BusinessException("El número de colegiatura ya está registrado");
        }

        Veterinario veterinario = Veterinario.builder()
                .usuario(usuario)
                .nombres(request.nombres())
                .apellidos(request.apellidos())
                .especialidad(request.especialidad())
                .numeroColegiatura(request.numeroColegiatura())
                .biografia(request.biografia())
                .aniosExperiencia(request.aniosExperiencia())
                .fotoPerfilUrl(request.fotoPerfilUrl())
                .build();

        Veterinario saved = veterinarioRepository.save(veterinario);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public VeterinarioResponse getProfileByUserId(Long userId) {
        Veterinario vet = veterinarioRepository.findByUsuarioId(userId)
                .orElseThrow(() -> new BusinessException("Perfil de veterinario no encontrado"));
        return mapToResponse(vet);
    }

    private VeterinarioResponse mapToResponse(Veterinario vet) {
        return new VeterinarioResponse(
                vet.getId(),
                vet.getUsuario().getId(),
                vet.getNombres(),
                vet.getApellidos(),
                vet.getEspecialidad(),
                vet.getNumeroColegiatura(),
                vet.getBiografia(),
                vet.getAniosExperiencia(),
                vet.getFotoPerfilUrl(),
                vet.getEstadoValidacion(),
                vet.getUsuario().getCorreo()
        );
    }
}