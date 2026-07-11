package com.pwb.backend.audio.internal.application.service;

import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.audio.internal.domain.exception.AudioErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipientAutocompleteService {

  private final JdbcTemplate jdbcTemplate;
  private final AudioProperties audioProperties;

  @Transactional(readOnly = true)
  public List<String> suggest(String producerId, String keyword) {
    if (keyword == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Query parameter q is required");
    }
    int min = audioProperties.getDistribution().getAutocompleteQueryMinLength();
    int max = audioProperties.getDistribution().getAutocompleteQueryMaxLength();
    if (keyword.length() < min || keyword.length() > max) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          "Query length must be between " + min + " and " + max);
    }
    String sanitized = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    String sql = "SELECT recipient_email FROM shared_threads "
        + "WHERE producer_id = ? AND recipient_email LIKE ? || '%' ESCAPE '\\' "
        + "AND deleted = false "
        + "ORDER BY last_interacted_at DESC LIMIT ?";
    int limit = audioProperties.getDistribution().getAutocompleteMaxResults();
    List<String> results = jdbcTemplate.queryForList(sql, String.class, producerId, sanitized, limit);
    log.info("SQL_LIKE_ESCAPE producerId={} rawInput={} sanitized={}",
        producerId, keyword, sanitized);
    return results;
  }
}
