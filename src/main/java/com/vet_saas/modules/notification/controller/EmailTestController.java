package com.vet_saas.modules.notification.controller;

import com.vet_saas.core.response.ApiResponse;
import com.vet_saas.modules.notification.service.EmailService;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test/email")
@RequiredArgsConstructor
public class EmailTestController {

    private final EmailService emailService;

    @GetMapping("/welcome")
    public ResponseEntity<ApiResponse<String>> testWelcomeEmail(
            @RequestParam String email,
            @RequestParam(required = false, defaultValue = "hola@huella360.com") String from) {
        
        Usuario usuario = new Usuario();
        usuario.setCorreo(email);
        
        // El EmailService actualmente usa el 'from' de las propiedades, 
        // pero para esta prueba forzaremos el remitente si se pasa por parámetro
        emailService.sendWelcomeEmail(usuario); 
        
        return ResponseEntity.ok(ApiResponse.success("Intento de envío de correo de bienvenida a " + email + " desde " + from));
    }
}
