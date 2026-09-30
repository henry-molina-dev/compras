package com.ferrocompras.ordenescompra.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "proveedor")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 30)
    private String telefono;

    @Column(length = 250)
    private String direccion;

    @Column(name = "webhook_api_key", unique = true, length = 100)
    private String webhookApiKey;

    // Cuanto se puede negociar sobre el precio de catalogo de cualquier producto de este proveedor,
    // en porcentaje. 0/0 = sin negociacion (se usa el precio de catalogo).
    @Builder.Default
    @Column(name = "descuento_maximo_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoMaximoPct = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "aumento_maximo_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal aumentoMaximoPct = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modificado_por_id")
    private Usuario modificadoPor;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_modificacion", nullable = false)
    private Instant fechaModificacion;
}
