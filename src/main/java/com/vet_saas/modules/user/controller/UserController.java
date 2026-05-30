package com.vet_saas.modules.user.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.user.model.Role;
import com.vet_saas.modules.user.model.Usuario;
import com.vet_saas.modules.user.repository.UsuarioRepository;
import com.vet_saas.modules.user.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;

    /**
     * GET /api/v1/users/me
     * Retorna la información básica del usuario autenticado, incluyendo su rol real.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMe(Principal principal) {
        Usuario usuario = usuarioService.findByCorreo(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                Map.of(
                        "id", usuario.getId(),
                        "correo", usuario.getCorreo(),
                        "rol", usuario.getRol().name()
                ),
                "Información del usuario"
        ));
    }

    /**
     * PATCH /api/v1/users/me/role
     * Permite a un usuario autenticado cambiar su rol UNA SOLA VEZ,
     * siempre y cuando su rol actual sea CLIENTE (valor por defecto al registrarse).
     *
     * Body: { "rol": "VETERINARIO" | "EMPRESA" | "REPARTIDOR" | "CLIENTE" }
     */
    @PatchMapping("/me/role")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, String>>> updateMyRole(
            @RequestBody Map<String, String> body,
            Principal principal) {

        String rolStr = body.get("rol");
        if (rolStr == null || rolStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo 'rol' es requerido");
        }

        Role nuevoRol;
        try {
            nuevoRol = Role.valueOf(rolStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido: " + rolStr);
        }

        // No permitir asignarse el rol ADMIN desde este endpoint
        if (nuevoRol == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes asignarte el rol ADMIN");
        }

        Usuario usuario = usuarioService.findByCorreo(principal.getName());

        // Solo permitir el cambio si el usuario aún tiene el rol por defecto (CLIENTE)
        // y no ha creado un perfil todavía
        if (usuario.getRol() != Role.CLIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tu rol ya está definido como " + usuario.getRol().name() + " y no puede ser cambiado desde aquí");
        }

        usuario.setRol(nuevoRol);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("rol", nuevoRol.name()),
                "Rol actualizado correctamente a " + nuevoRol.name()
        ));
    }
}