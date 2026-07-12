package com.turbotax.taxpayer.domain.entity;

import com.turbotax.taxpayer.domain.enums.TaxpayerType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "taxpayers", indexes = {
    @Index(name = "idx_taxpayer_tax_id_hash", columnList = "tax_id_hash", unique = true)
})
@Getter @Setter @NoArgsConstructor
@ToString(exclude = "accesses")
@EqualsAndHashCode(of = "id")
public class Taxpayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "taxpayer_type", nullable = false)
    private TaxpayerType taxpayerType;

    // SSN (individual) or EIN (business) — AES-256 via KMS, stored as base64
    @Column(name = "tax_id_encrypted", nullable = false, length = 512)
    private String taxIdEncrypted;

    // SHA-256 hash of raw SSN/EIN — used for DB lookups
    @Column(name = "tax_id_hash", nullable = false, length = 64)
    private String taxIdHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    // null for INDIVIDUAL — only set for BUSINESS
    @Column(name = "entity_type")
    private String entityType;

    // null for INDIVIDUAL — state of incorporation for BUSINESS
    @Column(name = "state_of_reg", length = 2)
    private String stateOfReg;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "taxpayer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserTaxpayerAccess> accesses = new ArrayList<>();
}
