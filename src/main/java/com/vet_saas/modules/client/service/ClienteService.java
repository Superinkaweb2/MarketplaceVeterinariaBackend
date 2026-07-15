package com.vet_saas.modules.client.service;

import com.vet_saas.config.AppProperties;
import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.core.exceptions.types.ForbiddenException;
import com.vet_saas.core.exceptions.types.ResourceNotFoundException;
import com.vet_saas.modules.client.dto.ClienteResponse;
import com.vet_saas.modules.client.dto.CreateClienteDto;
import com.vet_saas.modules.client.dto.UpdateClienteDto;
import com.vet_saas.modules.client.model.PerfilCliente;
import com.vet_saas.modules.client.repository.ClienteRepository;
import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.company.service.EmpresaLookupService;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vet_saas.modules.points.service.PointsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final EmpresaLookupService empresaLookupService;
    private final PointsService pointsService;
    private final AppProperties appProperties;

    @Transactional
    public ClienteResponse createPerfil(Usuario usuario, CreateClienteDto dto) {
        if (clienteRepository.existsByUsuarioIdAndActivoTrue(usuario.getId())) {
            throw new BusinessException("Ya tienes un perfil de cliente registrado.");
        }

        PerfilCliente perfil = PerfilCliente.builder()
                .usuario(usuario)
                .nombres(dto.nombres())
                .apellidos(dto.apellidos())
                .telefono(dto.telefono())
                .direccion(dto.direccion())
                .ciudad(dto.ciudad())
                .pais(dto.pais() != null ? dto.pais() : appProperties.getBusiness().getDefaultCountry())
                .activo(true)
                .build();

        perfil = clienteRepository.save(perfil);

        try {
            pointsService.addPoints(perfil.getId(), "REGISTRO", null, "Bono de bienvenida por registro");
        } catch (Exception e) {
            log.error("Error granting registration points: {}", e.getMessage());
        }

        return mapToResponse(perfil);
    }

    @Transactional(readOnly = true)
    public ClienteResponse getMyPerfil(Usuario usuario) {
        PerfilCliente perfil = clienteRepository.findByUsuarioIdAndActivoTrue(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PerfilCliente", "usuarioId", usuario.getId()));
        return mapToResponse(perfil);
    }

    @Transactional
    public ClienteResponse updateMyPerfil(Usuario usuario, UpdateClienteDto dto, String fotoUrl) {
        PerfilCliente perfil = clienteRepository.findByUsuarioIdAndActivoTrue(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PerfilCliente", "usuarioId", usuario.getId()));

        if (dto.nombres() != null) perfil.setNombres(dto.nombres());
        if (dto.apellidos() != null) perfil.setApellidos(dto.apellidos());
        if (dto.telefono() != null) perfil.setTelefono(dto.telefono());
        if (dto.direccion() != null) perfil.setDireccion(dto.direccion());
        if (dto.ciudad() != null) perfil.setCiudad(dto.ciudad());
        if (dto.pais() != null) perfil.setPais(dto.pais());
        if (fotoUrl != null) perfil.setFotoPerfilUrl(fotoUrl);

        return mapToResponse(clienteRepository.save(perfil));
    }

    @Transactional
    public void deleteMiPerfil(Usuario usuario) {
        PerfilCliente perfil = clienteRepository.findByUsuarioIdAndActivoTrue(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PerfilCliente", "usuarioId", usuario.getId()));
        perfil.setActivo(false);
        clienteRepository.save(perfil);
    }

    @Transactional(readOnly = true)
    public Page<ClienteResponse> getClientesByEmpresa(Usuario usuario, String q, Pageable pageable) {
        Empresa empresa = empresaLookupService.getEmpresaFromUsuario(usuario);
        return clienteRepository.findClientesByEmpresaId(empresa.getId(), q, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ClienteResponse getClienteByIdForEmpresa(Usuario usuario, Long perfilId) {
        Empresa empresa = empresaLookupService.getEmpresaFromUsuario(usuario);

        PerfilCliente perfil = clienteRepository.findById(perfilId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", "id", perfilId));

        boolean esClienteDeLaEmpresa = clienteRepository
                .findClientesByEmpresaId(empresa.getId(), null, Pageable.unpaged())
                .getContent()
                .stream()
                .anyMatch(c -> c.getId().equals(perfilId));

        if (!esClienteDeLaEmpresa) {
            throw new ForbiddenException("No tienes acceso al perfil de este cliente.");
        }

        return mapToResponse(perfil);
    }

    @Transactional(readOnly = true)
    public Page<ClienteResponse> searchClientes(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return clienteRepository.findAll(pageable).map(this::mapToResponse);
        }
        return clienteRepository.searchByNombre(q, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ClienteResponse getClienteById(Long id) {
        return clienteRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", "id", id));
    }

    private ClienteResponse mapToResponse(PerfilCliente perfil) {
        return new ClienteResponse(
                perfil.getId(),
                perfil.getUsuario().getId(),
                perfil.getUsuario().getCorreo(),
                perfil.getNombres(),
                perfil.getApellidos(),
                perfil.getTelefono(),
                perfil.getDireccion(),
                perfil.getCiudad(),
                perfil.getPais(),
                perfil.getFotoPerfilUrl(),
                perfil.getUbicacionLat(),
                perfil.getUbicacionLng(),
                perfil.getUpdatedAt());
    }
}
