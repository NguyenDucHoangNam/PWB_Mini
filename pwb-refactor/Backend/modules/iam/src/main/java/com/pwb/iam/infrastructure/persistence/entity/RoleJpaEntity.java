package com.pwb.iam.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "iam_roles",
        indexes = {
                @Index(name = "ix_iam_roles_name", columnList = "name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_iam_roles_name", columnNames = "name")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleJpaEntity extends IamJpaBaseEntity {

    @Column(name = "name", nullable = false, length = 32)
    private String name;

    @Column(name = "description", length = 255)
    private String description;
}
