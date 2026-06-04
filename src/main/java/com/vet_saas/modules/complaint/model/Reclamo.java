package com.vet_saas.modules.complaint.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reclamos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reclamo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Datos Reclamante
    private String tipoDocumento;
    private String numeroDocumento;
    private String primerNombre;
    private String segundoNombre;
    private String primerApellido;
    private String segundoApellido;
    private String direccion;
    private String departamento;
    private String provincia;
    private String distrito;
    private String correo;
    private String telefono;
    private Boolean esMenor;

    // Apoderado
    private String apoderadoTipoDocumento;
    private String apoderadoNumeroDocumento;
    private String apoderadoPrimerNombre;
    private String apoderadoSegundoNombre;
    private String apoderadoPrimerApellido;
    private String apoderadoSegundoApellido;
    private String apoderadoCorreo;
    private String apoderadoTelefono;

    // Info General
    private String numeroOrden;
    private BigDecimal montoReclamado;
    private String nombreProducto;

    // Detalle
    private String tipoReclamo; // RECLAMO o QUEJA
    private String resumen;
    
    @Column(columnDefinition = "TEXT")
    private String detallePedido;

    private String archivoAdjuntoUrl;

    @Column(name = "pdf_reclamo_url", columnDefinition = "TEXT")
    private String pdfReclamoUrl;

    @CreationTimestamp
    private LocalDateTime fechaRegistro;
}
