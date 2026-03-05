package com.vet_saas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private External external = new External();

    @Data
    public static class Jwt {
        private String secret;
        private long expiration;
        private long refreshExpiration;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins;
    }

    @Data
    public static class External {
        private CloudinaryProps cloudinary = new CloudinaryProps();
        private ApiPeruProps apiPeru = new ApiPeruProps();
        private MercadoPagoProps mercadoPago = new MercadoPagoProps();
        private MailProps mail = new MailProps();
        private String backendUrl;
        private String frontendUrl;
    }

    @Data
    public static class CloudinaryProps {
        private String cloudName;
        private String apiKey;
        private String apiSecret;
    }

    @Data
    public static class ApiPeruProps {
        private String baseUrl;
        private String token;
    }

    @Data
    public static class MercadoPagoProps {
        private String accessToken;
        private String clientId;
        private String clientSecret;
        private String sandboxBuyerEmail;
        private boolean sandbox = false;
    }

    @Data
    public static class MailProps {
        private String username;
        private String fromEmail;
    }
}