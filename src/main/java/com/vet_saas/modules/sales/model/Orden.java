package com.vet_saas.modules.sales.model;

import com.vet_saas.modules.company.model.Empresa;
import com.vet_saas.modules.user.model.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ordenes")
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden")
    private Long id;

    @Column(name = "codigo_orden", nullable = false, unique = true)
    private String codigoOrden; // Ej: ORD-2026-0001

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_cliente_id", nullable = false)
    private Usuario usuarioCliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "costo_envio", precision = 10, scale = 2)
    private BigDecimal costoEnvio;

    @Column(name = "comision_plataforma", precision = 10, scale = 2)
    private BigDecimal comisionPlataforma;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "order_status")
    private EstadoOrden estado;

    @Column(name = "metodo_pago")
    private String metodoPago;

    @Column(name = "mp_preference_id")
    private String mpPreferenceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "direccion_envio", columnDefinition = "jsonb")
    private Map<String, Object> direccionEnvio;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleOrden> detalles = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (estado == null)
            estado = EstadoOrden.PENDIENTE;
        if (costoEnvio == null)
            costoEnvio = BigDecimal.ZERO;
        if (comisionPlataforma == null)
            comisionPlataforma = BigDecimal.ZERO;
    }
}