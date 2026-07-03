package com.pwb.backend.modules.iam.internal.model;

import com.pwb.backend.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

  @Column(length = 50, unique = true, nullable = false)
  private String name;

  @Column(length = 255)
  private String description;
}
