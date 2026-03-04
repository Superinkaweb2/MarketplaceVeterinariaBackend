package com.vet_saas.modules.client.service;

import com.vet_saas.core.exceptions.types.BusinessException;
import com.vet_saas.core.exceptions.types.ForbiddenException;
import com.vet_saas.core.exceptions.types.ResourceNotFoundException;
import com.vet_saas.modules.client.dto.ClienteResponse;
import com.vet_saas.modules.client.dto.CreateClienteDto;
import com.vet_saas.modules.client.dto.UpdateClienteDto;
import com.vet_saas.modules.client.model.PerfilCliente;
import com.vet_saas.modules.client.repository.ClienteRepository;
import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.company.repository.EmpresaRepository;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final EmpresaRepository empresaRepository;

    /**
     * Crear perfil de cliente para el usuario autenticado (rol CLIENTE).
     * Solo se puede crear un perfil por usuario.
     */
    @Transactional
    public ClienteResponse createPerfil(Usuario usuario, CreateClienteDto dto) {
        if (clienteRepository.existsByUsuarioId(usuario.getId())) {
            throw new BusinessException("Ya tienes un perfil de cliente registrado.");
        }

        PerfilCliente perfil = PerfilCliente.builder()
                .usuario(usuario)
                .nombres(dto.nombres())
                .apellidos(dto.apellidos())
                .telefono(dto.telefono())
                .direccion(dto.direccion())
                .ciudad(dto.ciudad())
                .pais(dto.pais() != null ? dto.pais() : "Perú")
                .build();

        return mapToResponse(clienteRepository.save(perfil));
    }

    /**
     * Obtener el perfil propio del usuario autenticado (rol CLIENTE).
     */
    @Transactional(readOnly = true)
    public ClienteResponse getMyPerfil(Usuario usuario) {
        PerfilCliente perfil = clienteRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PerfilCliente", "usuarioId", usuario.getId()));
        return mapToResponse(perfil);
    }

    /**
     * Actualizar perfil propio del usuario autenticado (rol CLIENTE).
     */
    @Transactional
    public ClienteResponse updateMyPerfil(Usuario usuario, UpdateClienteDto dto, String fotoUrl) {
        PerfilCliente perfil = clienteRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PerfilCliente", "usuarioId", usuario.getId()));

        if (dto.nombres() != null)
            perfil.setNombres(dto.nombres());
        if (dto.apellidos() != null)
            perfil.setApellidos(dto.apellidos());
        if (dto.telefono() != null)
            perfil.setTelefono(dto.telefono());
        if (dto.direccion() != null)
            perfil.setDireccion(dto.direccion());
        if (dto.ciudad() != null)
            perfil.setCiudad(dto.ciudad());
        if (dto.pais() != null)
            perfil.setPais(dto.pais());

        if (fotoUrl != null) {
            perfil.setFotoPerfilUrl(fotoUrl);
        }

        return mapToResponse(clienteRepository.save(perfil));
    }

    /**
     * Eliminar perfil propio del usuario autenticado (hard delete).
     */
    @Transactional
    public void deleteMiPerfil(Usuario usuario) {
        PerfilCliente perfil = clienteRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PerfilCliente", "usuarioId", usuario.getId()));
        clienteRepository.delete(perfil);
    }

    // -------------------------------------------------------------------------
    // ENDPOINTS PARA ROL EMPRESA (read-only: ver sus clientes)
    // -------------------------------------------------------------------------

    /**
     * Listar los clientes que han realizado órdenes a la empresa autenticada.
     * Requiere rol EMPRESA.
     */
    @Transactional(readOnly = true)
    public Page<ClienteResponse> getClientesByEmpresa(Usuario usuario, String q, Pageable pageable) {
        Empresa empresa = getEmpresaFromUsuario(usuario);
        return clienteRepository.findClientesByEmpresaId(empresa.getId(), q, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Ver el perfil de un cliente específico (solo si pertenece a la empresa).
     * Requiere rol EMPRESA.
     */
    @Transactional(readOnly = true)
    public ClienteResponse getClienteByIdForEmpresa(Usuario usuario, Long perfilId) {
        Empresa empresa = getEmpresaFromUsuario(usuario);

        // Verificar que el perfil existe
        PerfilCliente perfil = clienteRepository.findById(perfilId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", "id", perfilId));

        // Verificar que ese cliente tiene órdenes con esta empresa
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

    // -------------------------------------------------------------------------
    // ADMIN
    // -------------------------------------------------------------------------

    /**
     * Buscar clientes por nombre o apellido (Admin).
     */
    @Transactional(readOnly = true)
    public Page<ClienteResponse> searchClientes(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return clienteRepository.findAll(pageable).map(this::mapToResponse);
        }
        return clienteRepository.searchByNombre(q, pageable).map(this::mapToResponse);
    }

    /**
     * Obtener perfil de cliente por id (Admin).
     */
    @Transactional(readOnly = true)
    public ClienteResponse getClienteById(Long id) {
        return clienteRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", "id", id));
    }

    // -------------------------------------------------------------------------
    // PRIVADOS
    // -------------------------------------------------------------------------

    private Empresa getEmpresaFromUsuario(Usuario usuario) {
        return empresaRepository.findByUsuarioPropietarioId(usuario.getId())
                .orElseThrow(() -> new BusinessException("Debes tener una empresa registrada para ver tus clientes."));
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
