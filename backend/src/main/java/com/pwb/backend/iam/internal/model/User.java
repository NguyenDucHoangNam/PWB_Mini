package com.pwb.backend.iam.internal.model;

import com.pwb.backend.iam.internal.enums.OAuthProvider;
import com.pwb.backend.iam.internal.enums.UserStatus;
import com.pwb.backend.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {

  @Column(length = 50, nullable = false)
  private String username;

  @Column(length = 100, nullable = false)
  private String email;

  @Column(length = 100)
  private String password;

  @Column(name = "full_name", length = 100, nullable = false)
  private String fullName;

  @Column(name = "avatar_url", length = 255)
  private String avatarUrl;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private UserStatus status = UserStatus.PENDING_VERIFICATION;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(name = "oauth_provider", length = 20, nullable = false)
  private OAuthProvider oauthProvider = OAuthProvider.LOCAL;

  @Column(name = "oauth_id", length = 100)
  private String oauthId;
}
