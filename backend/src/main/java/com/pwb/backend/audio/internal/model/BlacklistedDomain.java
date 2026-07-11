package com.pwb.backend.audio.internal.model;

import com.pwb.backend.shared.kernel.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "email_blacklisted_domains")
public class BlacklistedDomain extends BaseEntity {

  @Column(nullable = false, unique = true, length = 255)
  private String domain;

  @Column(length = 255)
  private String reason;
}
