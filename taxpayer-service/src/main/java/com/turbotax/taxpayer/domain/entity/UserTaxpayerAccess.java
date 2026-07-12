package com.turbotax.taxpayer.domain.entity;

import com.turbotax.taxpayer.domain.enums.AccessRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_taxpayer_access", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "taxpayer_id"})
})
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserTaxpayerAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxpayer_id", nullable = false)
    private Taxpayer taxpayer;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private AccessRole role;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "granted_at", updatable = false)
    private LocalDateTime grantedAt;
}
