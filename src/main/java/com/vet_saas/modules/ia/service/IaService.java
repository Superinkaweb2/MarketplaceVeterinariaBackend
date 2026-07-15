package com.vet_saas.modules.ia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vet_saas.config.AppProperties;
import com.vet_saas.modules.ia.dto.HealthAlertRequest;
import com.vet_saas.modules.ia.dto.HealthAlertResponse;
import com.vet_saas.modules.ia.dto.HealthAlertResponse.HealthAlert;
import com.vet_saas.modules.ia.model.IaUsage;
import com.vet_saas.modules.ia.repository.IaUsageRepository;
import com.vet_saas.modules.pet.model.Mascota;
import com.vet_saas.modules.pet.repository.MascotaRepository;
import com.vet_saas.modules.subscription.service.SubscriptionService;
import com.vet_saas.modules.user.model.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IaService {

    private final MascotaRepository mascotaRepository;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptionService;
    private final IaUsageRepository iaUsageRepository;

    @Transactional
    public HealthAlertResponse generateHealthAlerts(Usuario usuario, HealthAlertRequest request) {
        Mascota mascota = mascotaRepository.findById(request.mascotaId())
                .orElseThrow(() -> new com.vet_saas.core.exceptions.types.ResourceNotFoundException(
                        "Mascota", "id", request.mascotaId()));

        // Verificar límite de uso de IA por plan
        if (usuario.getRol() != null && usuario.getRol() == com.vet_saas.modules.user.model.Role.CLIENTE) {
            subscriptionService.enforceIaUsage(usuario.getId());
        }

        try {
            String prompt = buildHealthPrompt(mascota, request);
            String response = callOpenAiApi(prompt);
            if (response == null) {
                log.info("OpenAI returned null, using fallback alerts");
                return generateFallbackAlerts(mascota, request);
            }
            HealthAlertResponse result = parseAlertsResponse(response);
            trackUsage(usuario, request.mascotaId(), appProperties.getIa().getOpenaiModel(), true);
            return result;
        } catch (com.vet_saas.core.exceptions.types.BusinessException e) {
            throw e; // Re-throw plan limit errors
        } catch (Exception e) {
            log.error("Error generating health alerts: {}", e.getMessage());
            trackUsage(usuario, request.mascotaId(), "fallback", false);
            return generateFallbackAlerts(mascota, request);
        }
    }

    private void trackUsage(Usuario usuario, Long mascotaId, String modelo, boolean exitoso) {
        try {
            IaUsage usage = IaUsage.builder()
                    .usuario(usuario)
                    .mascotaId(mascotaId)
                    .modelo(modelo)
                    .exitoso(exitoso)
                    .build();
            iaUsageRepository.save(usage);
        } catch (Exception e) {
            log.warn("Failed to track IA usage: {}", e.getMessage());
        }
    }

    private String buildHealthPrompt(Mascota mascota, HealthAlertRequest request) {
        return String.format("""
                Eres un asistente veterinario de IA. Analiza la información de salud de la siguiente mascota y genera alertas de salud predictivas.

                Datos de la mascota:
                - Nombre: %s
                - Especie: %s
                - Raza: %s
                - Peso: %s kg
                - Edad: %s

                Último registro clínico:
                - Diagnóstico: %s
                - Tratamiento: %s
                - Notas: %s
                - Peso actual: %s kg

                Genera un JSON con alertas de salud. Cada alerta debe tener:
                - tipo: "VACUNA", "DESPARASITACION", "PESO", "SEGUIMIENTO", "PREVENTIVA"
                - severidad: "BAJA", "MEDIA", "ALTA"
                - titulo: título corto de la alerta
                - descripcion: descripción detallada
                - recomendacion: qué hacer

                Responde SOLO con el JSON, sin texto adicional.
                """,
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza() != null ? mascota.getRaza() : "No especificada",
                mascota.getPesoKg() != null ? mascota.getPesoKg() : "No registrado",
                mascota.getFechaNacimiento() != null ? mascota.getFechaNacimiento() : "No especificada",
                request.diagnostico() != null ? request.diagnostico() : "No especificado",
                request.tratamiento() != null ? request.tratamiento() : "No especificado",
                request.notas() != null ? request.notas() : "No hay notas",
                request.pesoKg() != null ? request.pesoKg() : "No registrado"
        );
    }

    private String callOpenAiApi(String prompt) {
        String apiKey = appProperties.getIa().getOpenaiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI API key not configured, returning null to trigger fallback");
            return null;
        }

        String model = appProperties.getIa().getOpenaiModel();
        String apiUrl = "https://api.openai.com/v1/chat/completions";

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt
                )),
                "temperature", 0.7,
                "max_tokens", 1000
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
        return response.getBody();
    }

    private HealthAlertResponse parseAlertsResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            // Clean the response - remove markdown code blocks if present
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            JsonNode alertsArray = objectMapper.readTree(content);
            List<HealthAlert> alerts = new ArrayList<>();

            if (alertsArray.isArray()) {
                for (JsonNode alertNode : alertsArray) {
                    alerts.add(HealthAlert.builder()
                            .tipo(alertNode.path("tipo").asText("SEGUIMIENTO"))
                            .severidad(alertNode.path("severidad").asText("BAJA"))
                            .titulo(alertNode.path("titulo").asText("Alerta de salud"))
                            .descripcion(alertNode.path("descripcion").asText(""))
                            .recomendacion(alertNode.path("recomendacion").asText(""))
                            .build());
                }
            }

            return HealthAlertResponse.builder()
                    .alertas(alerts)
                    .resumen("Se generaron " + alerts.size() + " alertas de salud")
                    .alertasGeneradas(alerts.size())
                    .build();
        } catch (Exception e) {
            log.error("Error parsing IA response: {}", e.getMessage());
            return generateDefaultAlerts();
        }
    }

    private HealthAlertResponse generateFallbackAlerts(Mascota mascota, HealthAlertRequest request) {
        List<HealthAlert> alerts = new ArrayList<>();

        if (mascota.getPesoKg() != null && mascota.getPesoKg().compareTo(java.math.BigDecimal.ZERO) > 0) {
            alerts.add(HealthAlert.builder()
                    .tipo("PESO")
                    .severidad("BAJA")
                    .titulo("Seguimiento de peso")
                    .descripcion("Peso actual: " + mascota.getPesoKg() + " kg. Mantenga un registro regular del peso de " + mascota.getNombre() + ".")
                    .recomendacion("Registre el peso mensualmente y consulte si hay cambios significativos.")
                    .build());
        }

        alerts.add(HealthAlert.builder()
                .tipo("PREVENTIVA")
                .severidad("BAJA")
                .titulo("Recordatorio de chequeo")
                .descripcion("Es recomendable realizar chequeos regulares para " + mascota.getNombre() + ".")
                .recomendacion("Programe una cita de control cada 6 meses o anualmente.")
                .build());

        return HealthAlertResponse.builder()
                .alertas(alerts)
                .resumen("Alertas generadas por lógica de respaldo")
                .alertasGeneradas(alerts.size())
                .build();
    }

    private HealthAlertResponse generateDefaultAlerts() {
        return HealthAlertResponse.builder()
                .alertas(List.of(HealthAlert.builder()
                        .tipo("SEGUIMIENTO")
                        .severidad("BAJA")
                        .titulo("Seguimiento básico")
                        .descripcion("Mantenga actualizado el historial clínico de su mascota.")
                        .recomendacion("Registre regularmente las visitas al veterinario.")
                        .build()))
                .resumen("Alertas por defecto")
                .alertasGeneradas(1)
                .build();
    }
}
