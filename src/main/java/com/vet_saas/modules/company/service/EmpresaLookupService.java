package com.vet_saas.modules.company.service;

import com.vet_saas.core.exceptions.types.ResourceNotFoundException;
import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.company.repository.EmpresaRepository;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmpresaLookupService {

    private final EmpresaRepository empresaRepository;

    @Transactional(readOnly = true)
    public Empresa getEmpresaFromUsuario(Usuario usuario) {
        return empresaRepository.findByUsuarioPropietarioId(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Empresa", "propietarioId", usuario.getId()));
    }

    @Transactional(readOnly = true)
    public Optional<Empresa> getEmpresaByUsuarioId(Long usuarioId) {
        return empresaRepository.findByUsuarioPropietarioId(usuarioId);
    }

    @Transactional(readOnly = true)
    public Empresa getEmpresaById(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", "id", empresaId));
    }
}
