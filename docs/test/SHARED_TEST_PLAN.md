# Shared Modules — Test Plan

> Test plan cho 3 shared modules: `shared-kernel`, `shared-web`, `shared-infrastructure`.
>
> Mỗi shared module được dùng bởi nhiều business module, nên test phải cover kỹ để không phá business modules khác.
>
> **Chuẩn áp dụng**: Test Pyramid (Martin Fowler) + FIRST (Tim Ottinger) + Right-BICEP (Brian Marick) + CORRECT (IEEE 829).
>
> **Nguyên tắc**: Mỗi test phải cover **một behavior**, không test implementation. Đọc code shared trước khi viết test.

---

## 1. Test Strategy

Xem chuẩn chi tiết tại [`TEST_GENERATION_RULES.md`](./TEST_GENERATION_RULES.md) §1, §3.6 (Right-BICEP), §3.7 (CORRECT), §15 (Tham chiếu chuẩn).

### 1.1 Tỷ lệ Pyramid cho Shared Modules

Shared modules đặc biệt hơn business module vì **nhiều module khác phụ thuộc vào**. Bug ở shared = bug lan ra toàn hệ thống. Do đó áp dụng tỷ lệ Pyramid **nặng về Unit + Integration**:

```
                        ▲
                       ╱ ╲
                      ╱ E2E ╲              ←  5% — full flow qua shared filter/resolver
                     ╱───────╲
                    ╱ Contract ╲           ←  5% — HTTP contract của shared filter
                   ╱───────────╲
                  ╱ Integration  ╲         ← 25% — adapter với container
                 ╱─────────────────╲        (cao hơn IAM vì shared phải test container)
                ╱  Component (Slice)╲      ← 15% — @WebMvcTest filter/resolver
               ╱─────────────────────╲
              ╱      Unit Test          ╲  ← 50% — pure logic, POJO thuần
             ╱───────────────────────────╲
```

**Lý do tỷ lệ khác IAM**:
- shared-kernel là POJO thuần → **70% unit** (pure logic)
- shared-infrastructure cần test container **thật** (Redis/Kafka/MinIO) → **25% integration**
- shared-web filter/resolver cần test qua MockMvc → **15% component**

### 1.2 Coverage Targets

| Module | Target | Lý do |
|--------|--------|-------|
| `shared-kernel/` (POJO) | ≥ 90% | Pure logic, dễ test, không excuse |
| `shared-web/` (filter/resolver/handler) | ≥ 80% | Cross-cutting concerns |
| `shared-infrastructure/` (adapter) | ≥ 70% | Test qua IT với container |
| **Mutation score (PIT)** | ≥ 70% | shared bug ảnh hưởng nhiều module |

### 1.3 Special Rules cho Shared Modules

| Rule | Lý do |
|------|-------|
| shared-kernel test KHÔNG dùng Spring annotation | Pure POJO, phải test độc lập |
| shared-web test filter phải dùng `MockMvc` hoặc `MockHttpServletRequest` thuần | Verify thật sự qua HTTP, không mock |
| shared-infrastructure test BẮT BUỘC dùng Testcontainers | Logic container (Lua, TTL, transactional) không mock được |
| Mỗi shared module test **độc lập** | KHÔNG depend vào IAM module |
| Khi shared fix bug → verify business modules không break | Re-run `mvn verify` trên toàn project |

---

## 2. Cấu trúc thư mục Test

```
pwb-refactor/Backend/
├── shared/
│   ├── shared-kernel/
│   │   └── src/test/java/com/pwb/shared/
│   │       ├── exception/
│   │       │   ├── BusinessExceptionTest.java
│   │       │   ├── ErrorCodeTest.java
│   │       │   └── ValidationExceptionTest.java
│   │       └── dto/
│   │           ├── ApiResponseTest.java
│   │           ├── ErrorInfoTest.java
│   │           └── PageResponseTest.java
│   ├── shared-web/
│   │   └── src/test/java/com/pwb/web/
│   │       ├── exception/
│   │       │   └── GlobalExceptionHandlerTest.java
│   │       ├── filter/
│   │       │   └── CorrelationIdFilterTest.java
│   │       ├── security/
│   │       │   ├── CurrentUserArgumentResolverTest.java
│   │       │   ├── CurrentClientIpArgumentResolverTest.java
│   │       │   ├── JwtAuthenticationFilterTest.java      ← NEW
│   │       │   └── JwtTokenValidatorTest.java            ← NEW
│   │       └── i18n/
│   │           └── MessageResolverTest.java
│   └── shared-infrastructure/
│       └── src/test/java/com/pwb/infra/
│           ├── redis/
│           │   ├── RedisConfigTest.java
│           │   └── RedisKeyPatternsTest.java
│           ├── outbox/
│           │   ├── OutboxJpaWriterTest.java
│           │   ├── LoggingOutboxWriterTest.java
│           │   ├── OutboxRelaySchedulerIT.java
│           │   ├── KafkaOutboxPublisherIT.java
│           │   ├── OutboxEnqueueHelperTest.java
│           │   └── OutboxEventDeduplicatorTest.java      ← NEW
│           ├── mail/
│           │   ├── OutboxEmailEnqueueListenerTest.java
│           │   ├── MailKafkaConsumerIT.java
│           │   ├── ThymeleafEmailRendererTest.java
│           │   └── EmailTemplateAllowlistTest.java        ← NEW (security)
│           ├── storage/
│           │   ├── S3StorageServiceImplIT.java
│           │   ├── MediaTypeUtilsTest.java
│           │   └── FileTypeValidatorTest.java             ← NEW (security)
│           └── kafka/
│               ├── KafkaConfigTest.java
│               ├── KafkaConsumerConfigIT.java
│               └── KafkaErrorHandlerTest.java             ← NEW (DLQ logic)
```

---

## 3. Unit Test (50% — pure logic, POJO thuần)

### 3.1 shared-kernel — Unit Test (70% của module này)

Áp dụng chuẩn **Right-BICEP + CORRECT** — đặc biệt **B** (boundary), **E** (error), **C** (conformance).

#### 3.1.1 `BusinessExceptionTest`

**Right-BICEP**: B, I, E | **CORRECT**: O, E, C

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_carry_error_code_in_message` | O | `getErrorCode().code()` = code được truyền vào |
| `should_use_error_code_default_message_when_no_override` | B | null override → defaultMessage |
| `should_carry_metadata_as_immutable_map` | I | truyền `Map.of("k", "v")` → immutable |
| `should_throw_npe_when_error_code_null` | E | null errorCode → NPE |
| `should_handle_empty_metadata` | B | không truyền metadata → `Map.of()` |
| `should_reject_null_metadata_values` | E | metadata chứa null value → reject |
| `should_serialize_metadata_as_immutable_after_construction` | C | modify bên ngoài không ảnh hưởng |
| `should_preserve_insertion_order_in_metadata` | O | LinkedHashMap order |

#### 3.1.2 `ErrorCodeTest`

**CORRECT**: C, O

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_category_for_each_error_code` | C | duyệt enum → mỗi value có `category()` |
| `should_return_unique_codes` | C | tất cả `code()` unique |
| `should_return_non_blank_default_message` | C | defaultMessage không blank |
| `should_throw_when_category_null` | E | constructor validation |
| `should_return_HTTP_status_matching_category` | C | mapping category → HTTP status đúng |
| `should_never_return_null_code` | C | non-null |
| `should_follow_naming_convention_for_codes` | C | uppercase + underscore |

#### 3.1.3 `ValidationExceptionTest`

**Right-BICEP**: E, B | **CORRECT**: E

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_carry_field_errors` | O | List<FieldError> |
| `should_use_default_message_validation_failed` | B | fallback |
| `should_reject_null_field_errors` | E | null → throw |
| `should_carry_bean_name` | O | bean name OK |

#### 3.1.4 `ApiResponseTest`

**CORRECT**: C, O

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `success_should_return_wrapped_data_with_success_true` | O | `success=true`, `data=userDto`, `error=null`, `traceId=null` |
| `error_should_return_with_success_false` | O | `success=false`, `data=null`, `error=ErrorInfo(...)` |
| `should_carry_trace_id` | O | MDC traceId |
| `should_have_immutable_data` | C | record immutability |
| `should_serialize_to_json_correctly` | C | Jackson round-trip |
| `should_deserialize_from_json_correctly` | I | reverse |

#### 3.1.5 `ErrorInfoTest`

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_carry_code_message_metadata` | O | constructor + getter |
| `should_have_immutable_metadata` | C | record |

#### 3.1.6 `PageResponseTest`

**CORRECT**: R (range), C (cardinality), B (boundary)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_compute_total_pages_correctly` | R | `totalItems=100, size=10` → `totalPages=10` |
| `should_compute_total_pages_with_remainder` | R | `totalItems=101, size=10` → `totalPages=11` |
| `should_return_zero_pages_when_empty` | B | `totalItems=0` → `totalPages=0` |
| `should_handle_items_list_size_different_from_size_field` | C | inconsistency OK |
| `should_throw_when_size_zero` | E | size = 0 → IAE |
| `should_throw_when_size_negative` | E | size < 0 → IAE |
| `should_handle_page_index_out_of_range` | B | page > totalPages → empty items |
| `should_compute_hasNext_and_hasPrevious_correctly` | O | boolean flags |

---

### 3.2 shared-infrastructure — Unit Test (không cần container)

#### 3.2.1 `RedisKeyPatternsTest` (unit, no container)

**CORRECT**: C (conformance)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_build_correct_prefix_for_iam_keys` | C | prefix `pwb:iam:*` |
| `should_build_correct_prefix_for_outbox_keys` | C | prefix `pwb:outbox:*` |
| `should_build_correct_prefix_for_storage_keys` | C | prefix `pwb:storage:*` |
| `should_handle_null_namespace_gracefully` | E | null → "default" |
| `should_sanitize_input_to_prevent_key_injection` | E | `:` / `*` / `\n` → escape |

#### 3.2.2 `LoggingOutboxWriterTest` (unit)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_log_event_when_writing` | O | log.info() called |
| `should_not_throw_when_logging_fails` | E | graceful |
| `should_not_log_sensitive_payload_data` | E | password/token không log |

#### 3.2.3 `OutboxEnqueueHelperTest` (unit)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_delegate_to_outbox_writer` | O | delegation |
| `should_capture_aggregate_type_id_event_type_payload` | O | capture đúng |
| `should_set_timestamp_now` | T | `createdAt = now` |

#### 3.2.4 `OutboxEventDeduplicatorTest` (unit, NEW)

**CORRECT**: O (ordering), C (cardinality)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_true_when_duplicate_within_dedup_window` | C | hash match → dedup |
| `should_return_false_when_unique_event` | C | new event OK |
| `should_allow_duplicate_after_dedup_window_expires` | T | TTL expiry |
| `should_use_aggregate_id_as_partition_key` | O | same partition → dedup |
| `should_handle_concurrent_dedup_check` | C | thread-safe (atomic) |

#### 3.2.5 `ThymeleafEmailRendererTest` (unit)

**Right-BICEP**: E, B | **CORRECT**: C

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_render_text_template` | O | text output |
| `should_render_html_template` | O | HTML output |
| `should_substitute_variables` | O | `{{var}}` → value |
| `should_throw_template_not_found_when_missing` | E | IOException / TemplateException |
| `should_escape_html_in_user_input` | C | XSS prevention |
| `should_handle_null_variables_gracefully` | B | null → empty string |
| `should_render_with_locale_specific_template` | O | vi_VN vs en_US |

#### 3.2.6 `OutboxEmailEnqueueListenerTest` (unit)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_convert_email_event_to_enqueue_command` | O | mapping đúng |
| `should_handle_missing_locale_as_default` | B | null locale → vi |
| `should_validate_required_fields_present` | E | missing recipient → throw |

#### 3.2.7 `EmailTemplateAllowlistTest` (unit, NEW — security)

**CORRECT**: C, E

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_allow_template_in_allowlist` | O | allow |
| `should_reject_template_not_in_allowlist` | E | reject |
| `should_reject_template_with_path_traversal` | E | `../../etc/passwd` → reject |
| `should_resolve_template_by_short_name` | O | "welcome" → "welcome.html" |

#### 3.2.8 `MediaTypeUtilsTest` (unit)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_resolve_content_type_from_extension` | O | `.pdf` → `application/pdf` |
| `should_default_to_octet_stream_when_unknown` | B | unknown → `application/octet-stream` |
| `should_handle_uppercase_extension` | B | `.PDF` → `application/pdf` |
| `should_handle_null_extension` | E | null → `application/octet-stream` |

#### 3.2.9 `FileTypeValidatorTest` (unit, NEW — security)

**Right-BICEP**: E, B | **CORRECT**: C

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_allow_file_in_allowlist` | O | allow |
| `should_reject_executable_file` | E | `.exe`, `.bat` → reject |
| `should_reject_script_file` | E | `.js`, `.php`, `.sh` → reject |
| `should_reject_file_with_double_extension` | E | `image.jpg.exe` → reject |
| `should_validate_magic_bytes_for_image` | C | cross-check magic bytes với extension |
| `should_reject_file_with_null_filename` | E | null → reject |
| `should_reject_file_with_empty_filename` | E | "" → reject |
| `should_reject_filename_with_path_traversal` | E | `../../etc/passwd` → reject |

#### 3.2.10 `KafkaErrorHandlerTest` (unit, NEW)

**Right-BICEP**: E | **CORRECT**: R (reference)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_route_to_dlq_when_deserialization_fails` | E | poison message → DLQ |
| `should_retry_when_transient_failure` | E | retry with backoff |
| `should_route_to_dlq_after_max_retries_exceeded` | C | 3 retry → DLQ |
| `should_not_block_partition_on_permanent_failure` | R | skip + commit offset |

---

## 4. Component Test (15% — slice test với Spring)

### 4.1 shared-web — Component Test

#### 4.1.1 `GlobalExceptionHandlerTest` (`@WebMvcTest`)

> **Setup**: `@WebMvcTest` với 1 controller dummy để trigger exception. Mock `MessageSource` để trả message cố định.

**CORRECT**: C (conformance), O (ordering)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_409_when_business_exception_conflict` | C | dummy throw `EMAIL_ALREADY_REGISTERED` → response status 409, `success=false`, `error.code="IAM_001"` |
| `should_return_404_when_business_exception_not_found` | C | NOT_FOUND → 404 |
| `should_return_400_when_business_exception_validation` | C | VALIDATION → 400 |
| `should_return_401_when_business_exception_unauthorized` | C | UNAUTHORIZED → 401 |
| `should_return_403_when_business_exception_forbidden` | C | FORBIDDEN → 403 |
| `should_return_429_when_business_exception_too_many_requests` | C | TOO_MANY → 429, có `retryAfterSeconds` |
| `should_return_500_when_business_exception_internal` | C | INTERNAL → 500 |
| `should_resolve_message_via_message_source` | O | `MessageSource.getMessage(key, null, locale)` được dùng |
| `should_use_default_message_when_message_source_returns_null` | B | fallback |
| `should_carry_metadata_in_error_response` | O | metadata → response |
| `should_carry_trace_id_in_error_response` | O | MDC `correlationId` → response `traceId` |
| `should_not_leak_internal_exception_message_in_500_response` | E | security — chỉ trả generic message |
| `should_handle_unexpected_exception_gracefully` | E | `NullPointerException` → 500, không crash |
| `should_sanitize_trace_id_to_prevent_injection` | E | security — XSS trong log/response |

#### 4.1.2 `CorrelationIdFilterTest`

**CORRECT**: C (conformance), O (ordering)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_use_header_correlation_id_when_present` | O | header `X-Correlation-ID: abc-123` → response cùng header; MDC `correlationId=abc-123` |
| `should_generate_uuid_when_header_missing` | B | UUID v4 format |
| `should_clear_mdc_after_request_completed` | O | MDC clean sau filter |
| `should_handle_multiple_concurrent_requests_with_different_ids` | C | thread-local không leak |
| `should_reject_malformed_correlation_id` | E | chỉ accept UUID/safe chars |
| `should_sanitize_correlation_id_to_prevent_log_injection` | E | security — `\n` / control chars |
| `should_run_before_other_filters` | O | ordering trong filter chain |
| `should_handle_request_when_dispatcher_throws` | E | finally block clear MDC |

#### 4.1.3 `CurrentUserArgumentResolverTest`

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_support_parameter_with_currentuser_annotation_and_uuid_type` | O | resolve OK |
| `should_not_support_parameter_without_annotation` | E | return false |
| `should_not_support_parameter_with_wrong_type` | E | String → reject |
| `should_resolve_userId_from_security_context` | O | SecurityContext → userId |
| `should_return_null_when_not_authenticated` | B | anonymous → null |
| `should_throw_when_security_context_has_wrong_principal_type` | E | type mismatch |
| `should_run_after_jwt_filter` | O | ordering |

#### 4.1.4 `CurrentClientIpArgumentResolverTest`

**Right-BICEP**: B, E | **CORRECT**: C

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_extract_ip_from_x_forwarded_for_header` | O | `X-Forwarded-For: 192.168.1.1, 10.0.0.1` → `192.168.1.1` |
| `should_extract_ip_from_x_real_ip_header` | O | `X-Real-IP: 1.2.3.4` → `1.2.3.4` |
| `should_return_unknown_when_no_header` | B | null → "unknown" |
| `should_handle_ipv6_address` | B | `[::1]` → `::1` |
| `should_handle_trusted_proxy_chain` | C | chỉ lấy IP đầu tiên (client) |
| `should_reject_malformed_x_forwarded_for` | E | security — chỉ lấy IP valid |
| `should_reject_localhost_spoofing` | E | security — không tin `127.0.0.1` qua proxy |
| `should_fallback_to_remote_addr_when_proxy_header_malformed` | B | `request.getRemoteAddr()` |

#### 4.1.5 `MessageResolverTest`

**CORRECT**: C, O

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_resolve_message_with_default_locale` | O | locale = null → default |
| `should_resolve_message_with_vi_locale` | O | vi |
| `should_resolve_message_with_en_locale` | O | en |
| `should_fallback_to_key_when_message_missing` | B | return key |
| `should_format_message_with_args` | O | `{0}`, `{1,date}` |
| `should_extract_locale_from_accept_language_header` | C | "vi-VN,vi;q=0.9,en;q=0.8" → vi |
| `should_fallback_locale_when_unsupported` | B | "fr" → default (vi) |
| `should_cache_resolved_messages` | P | performance |

#### 4.1.6 `JwtAuthenticationFilterTest` (NEW)

**CORRECT**: C, O, E

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_extract_jwt_from_authorization_header` | C | `Bearer xxx` → token |
| `should_extract_jwt_from_cookie_when_no_header` | O | cookie fallback |
| `should_set_security_context_when_token_valid` | O | valid → SecurityContextHolder |
| `should_not_set_security_context_when_token_invalid` | E | invalid → anonymous |
| `should_skip_filter_when_path_in_whitelist` | O | `/auth/login` → skip |
| `should_reject_when_token_signature_invalid` | E | tampered → 401 |
| `should_reject_when_token_expired` | T | expired → 401 |
| `should_handle_malformed_authorization_header` | E | "Token xxx" → ignore |

#### 4.1.7 `JwtTokenValidatorTest` (NEW)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_validate_jwt_with_correct_secret` | C | HS256 valid |
| `should_reject_jwt_with_wrong_secret` | E | signature mismatch |
| `should_reject_expired_jwt` | T | expired |
| `should_reject_jwt_with_wrong_issuer` | E | iss mismatch |
| `should_reject_jwt_with_wrong_audience` | E | aud mismatch |
| `should_extract_claims_when_valid` | O | subject, custom claims |
| `should_handle_jwt_with_no_signature` | E | unsigned JWT reject |

---

## 5. Integration Test (25% — adapter với container)

### 5.1 shared-infrastructure — Integration Test

#### 5.1.1 `RedisConfigTest` (`@SpringBootTest` minimal)

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_create_redis_template_bean` | O | bean exists |
| `should_create_connection_factory_with_configured_properties` | O | config match |

#### 5.1.2 `OutboxJpaWriterTest` (`@DataJpaTest` + H2)

**CORRECT**: O, T, E

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_persist_event_with_pending_status` | O | row status = PENDING |
| `should_set_next_retry_at_when_failing` | T | retry_at = now + backoff |
| `should_carry_aggregate_type_and_id` | O | capture |
| `should_serialize_payload_as_json` | C | Jackson serialize |
| `should_throw_when_persistence_fails` | E | exception propagated |
| `should_use_aggregate_id_as_partition_key` | O | Kafka partition |
| `should_set_created_at_to_now` | T | timestamp |

#### 5.1.3 `OutboxRelaySchedulerIT` (Testcontainers Postgres + Kafka)

**Right-BICEP**: E, P | **CORRECT**: O, R, T, C

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_publish_pending_events_to_kafka` | O | 1. Insert PENDING; 2. Trigger scheduler; 3. Kafka topic có message; 4. DB update PUBLISHED |
| `should_not_publish_already_published_events` | E | skip PUBLISHED rows |
| `should_retry_failed_events_with_exponential_backoff` | T | retry count tăng, next_retry_at update |
| `should_handle_kafka_unavailable_gracefully` | E | stop Kafka container → retry count tăng, status PENDING |
| `should_handle_outbox_event_larger_than_kafka_max_size` | B | > 1MB → DLQ |
| `should_batch_process_pending_events` | P | 1000 events → batch 100 |
| `should_run_on_multiple_instances_without_duplicate_publish` | C | cluster-safe |
| `should_publish_in_order_within_same_partition` | O | ordering preserved |

#### 5.1.4 `KafkaOutboxPublisherIT`

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_publish_to_correct_topic` | O | topic match |
| `should_include_partition_key_from_aggregate_id` | O | partition key = aggregate_id |
| `should_set_headers_from_outbox_metadata` | O | headers OK |
| `should_throw_publish_exception_on_kafka_failure` | E | propagation |

#### 5.1.5 `MailKafkaConsumerIT` (Testcontainers Kafka)

**Right-BICEP**: E | **CORRECT**: O, E, T

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_consume_email_event_from_kafka` | O | consumer receive |
| `should_render_template_with_variables` | O | render OK |
| `should_send_via_smtp_mock` | O | SMTP send |
| `should_dlq_when_template_not_found` | E | route to DLQ topic |
| `should_dlq_when_render_fails` | E | route to DLQ |
| `should_handle_malformed_payload_gracefully` | E | poison message → DLQ |
| `should_retry_with_backoff_on_smtp_transient_failure` | T | retry 3 times |
| `should_commit_offset_only_after_successful_send` | O | no message loss |
| `should_idempotent_when_same_event_consumed_twice` | C | dedup |

#### 5.1.6 `S3StorageServiceImplIT` (Testcontainers MinIO)

> **Container**: `GenericContainer("minio/minio:latest").withEnv("MINIO_ROOT_USER", "test").withEnv("MINIO_ROOT_PASSWORD", "testtest")`

**Right-BICEP**: B, E | **CORRECT**: C, O, E

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_upload_file_and_return_object_key` | O | key returned |
| `should_download_uploaded_file_with_correct_content` | C | round-trip |
| `should_generate_presigned_url_with_expiry` | T | URL + expiry |
| `should_delete_existing_object` | O | delete OK |
| `should_get_metadata_of_existing_object` | O | metadata OK |
| `should_throw_when_object_not_found` | E | not found → throw |
| `should_respect_content_type_from_request` | C | content-type OK |
| `should_handle_large_file_multipart_upload` | B | > 5MB → multipart |
| `should_handle_minio_bucket_not_exists` | E | create or fail gracefully |
| `should_throw_when_upload_too_large` | B | > max size → reject |
| `should_validate_file_type_against_allowlist` | E | `.exe` reject |
| `should_handle_presigned_url_expired` | T | expired → 403 |

#### 5.1.7 `KafkaConsumerConfigIT` (Testcontainers Kafka)

**CORRECT**: C, O, E

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_consume_message_with_correct_deserializer` | C | deserialize OK |
| `should_handle_deserialization_failure_to_dlq` | E | poison → DLQ |
| `should_commit_offset_after_successful_processing` | O | no duplicate |
| `should_not_commit_offset_on_failure` | E | retry on next poll |
| `should_handle_consumer_rebalance_gracefully` | C | partition revoked → rebalance |
| `should_handle_offset_commit_failure` | E | commit fail → re-poll |

---

## 6. Contract Test (5% — shared HTTP contract)

### 6.1 Shared Filter Contract

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_correlation_id_header_on_every_response` | C | header present |
| `should_return_correlation_id_in_error_body` | C | traceId field |
| `should_return_standard_error_format_for_all_exceptions` | C | ApiResponse error format |
| `should_use_iso8601_for_timestamps` | C | date format |
| `should_handle_utf8_in_error_messages` | C | encoding |

---

## 7. E2E Test (5% — full flow qua shared layer)

### 7.1 `SharedFilterChainIT` (`@SpringBootTest` + Testcontainers)

> **Mục đích**: Verify shared filter chain hoạt động đúng khi integrate với nhau.

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_run_filters_in_correct_order` | O | CorrelationId → Logging → Jwt → Controller |
| `should_propagate_correlation_id_through_async_tasks` | C | MDC propagated |
| `should_handle_request_when_jwt_filter_throws` | E | exception → GlobalExceptionHandler |
| `should_continue_filter_chain_when_optional_filter_fails` | E | graceful |

### 7.2 `OutboxKafkaMailIT` (full pipeline)

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_persist_outbox_then_relay_then_send_email` | O, R | 1. Use case → outbox row; 2. Relay → Kafka; 3. Mail consumer → SMTP |
| `should_dlq_email_when_template_not_found` | E | render fail → DLQ |
| `should_retry_email_send_on_smtp_transient_failure` | T | retry 3 times → success |

### 7.3 `UploadDownloadIT` (full pipeline)

| Test case | Chuẩn | Flow |
|-----------|-------|------|
| `should_upload_file_and_download_via_presigned_url` | O, R | upload → generate URL → download |
| `should_reject_upload_with_disallowed_type` | E | `.exe` → 400 |

---

## 8. Non-functional Test

### 8.1 Performance Test

| Test case | Tool | SLA |
|-----------|------|-----|
| `should_handle_5000_RPS_through_filter_chain` | Gatling | p99 < 50ms overhead |
| `should_handle_1000_RPS_outbox_relay` | Gatling | batch process |
| `should_handle_500_concurrent_uploads_to_s3` | Gatling | < 2s per upload |

### 8.2 Security Test

| Test case | Tool | Scope |
|-----------|------|-------|
| `should_prevent_log_injection_via_correlation_id` | Custom | `\n` → escape |
| `should_prevent_path_traversal_in_filename` | OWASP ZAP | `../../../etc/passwd` |
| `should_prevent_xss_in_error_message` | OWASP ZAP | `<script>` → escape |
| `should_prevent_xxe_in_soap_payload` | OWASP ZAP | SOAP endpoints |
| `should_prevent_email_header_injection` | Custom | `\n` in recipient |
| `should_prevent_kafka_payload_explosion` | Custom | large payload |
| `should_sanitize_outbox_metadata_to_prevent_log_injection` | Custom | All loggers |

### 8.3 i18n Test

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_return_vi_message_for_vi_locale` | O | vi message |
| `should_return_en_message_for_en_locale` | O | en message |
| `should_fallback_to_key_when_translation_missing` | B | fallback OK |
| `should_not_throw_when_message_source_unavailable` | E | graceful |
| `should_handle_complex_message_with_args` | C | multi-arg |

### 8.4 Time & Concurrency Test

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_handle_clock_skew_in_token_expiry` | T | skew tolerance |
| `should_handle_concurrent_outbox_writes` | C | race condition safe |
| `should_handle_concurrent_outbox_relay` | C | no duplicate publish |
| `should_handle_concurrent_correlation_id_generation` | C | unique IDs |
| `should_handle_concurrent_uploads_to_same_bucket` | C | no race |

### 8.5 Idempotency Test

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_deduplicate_outbox_events_with_same_aggregate_id` | O | dedup |
| `should_deduplicate_email_send_for_same_event` | C | mail dedup |
| `should_idempotent_on_storage_upload` | C | same key overwrite |

### 8.6 Failure Recovery Test

| Test case | Chuẩn | Verify |
|-----------|-------|--------|
| `should_fail_open_when_redis_pool_exhausted_for_non_critical` | E | graceful fallback |
| `should_fail_closed_when_redis_unavailable_for_critical` | E | BusinessException(SERVICE_UNAVAILABLE) |
| `should_recover_after_redis_transient_failure` | T | retry → success |
| `should_recover_after_kafka_broker_restart` | T | reconnect |
| `should_recover_after_minio_restart` | T | reconnect |
| `should_handle_outbox_relay_during_kafka_outage` | E | retry → success after recovery |

---

## 9. Test Infrastructure

### 9.1 Base class cho IT cần Postgres + Redis + Kafka

```java
@Testcontainers
public abstract class AbstractFullStackIT {
    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pwb_test");

    @Container
    static GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Container
    static KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    static GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:latest")
        .withEnv("MINIO_ROOT_USER", "testadmin")
        .withEnv("MINIO_ROOT_PASSWORD", "testadmin")
        .withCommand("server", "/data")
        .withExposedPorts(9000);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("pwb.storage.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getFirstMappedPort());
        registry.add("pwb.storage.access-key", () -> "testadmin");
        registry.add("pwb.storage.secret-key", () -> "testadmin");
    }
}
```

### 9.2 Base class cho IT chỉ cần Redis

```java
@Testcontainers
public abstract class AbstractRedisIT {
    @Container
    static GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }
}
```

### 9.3 Base class cho IT chỉ cần Kafka

```java
@Testcontainers
public abstract class AbstractKafkaIT {
    @Container
    static KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
```

### 9.4 Stub & Helper

```java
// Stub MessageSource cho GlobalExceptionHandler test
public class StubMessageSource implements MessageSource {
    private final Map<String, String> messages = new HashMap<>();

    public StubMessageSource withMessage(String key, String message) {
        messages.put(key, message);
        return this;
    }

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        return messages.getOrDefault(code, defaultMessage);
    }
}

// In-memory StorageService cho test không cần S3
public class InMemoryStorageService implements StorageService {
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public UploadResult upload(InputStream input, String filename, String contentType) {
        byte[] bytes = input.readAllBytes();
        String key = UUID.randomUUID().toString() + "/" + filename;
        objects.put(key, bytes);
        return new UploadResult(key, filename, contentType, bytes.length);
    }

    // ... các method khác
}

// Stub KafkaProducer cho outbox test
public class StubKafkaProducer implements KafkaProducer {
    private final List<Record> records = new ArrayList<>();

    @Override
    public void send(String topic, String key, Object payload) {
        records.add(new Record(topic, key, payload));
    }

    public List<Record> getRecords() { return records; }
}
```

---

## 10. Dependencies cần thêm vào 3 shared modules

### 10.1 `shared-kernel/pom.xml`

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

### 10.2 `shared-web/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <scope>test</scope>
</dependency>
```

### 10.3 `shared-infrastructure/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>minio</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-junit5-plugin</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 11. Quy tắc đặc biệt cho Shared Modules (đọc kỹ)

1. **shared-kernel**: KHÔNG dùng Spring annotation trong test. Pure JUnit + AssertJ.
2. **shared-web**: Test filter/resolver phải dùng `MockMvc` hoặc `MockHttpServletRequest`/`Response` thuần.
3. **shared-infrastructure**: BẮT BUỘC dùng Testcontainers cho bất kỳ test nào chạm Redis/Kafka/S3.
4. **Mỗi shared module test độc lập**: KHÔNG phụ thuộc IAM module khi test shared-web hay shared-infrastructure.
5. **Khi shared fix bug → verify business modules không break**: Re-run `mvn verify` trên toàn project.
6. **Mỗi test phải được tag chuẩn** (Right-BICEP letter hoặc CORRECT letter) trong table test plan.
7. **Tag test quan trọng** cho shared (vì ảnh hưởng nhiều module): security test, boundary test, error path.
8. **Mock boundary, không mock value object**: Mock `MessageSource`, `JwtDecoder`. KHÔNG mock `BusinessException`, `ApiResponse`, `ErrorInfo`.

---

## 12. Definition of Done

Shared modules được coi là "test chuẩn Product" khi:

- [ ] Tất cả test case trong file này được implement
- [ ] `mvn test` pass 100% trên cả 3 shared modules
- [ ] `mvn verify` pass (bao gồm IT với Testcontainers)
- [ ] Coverage domain (shared-kernel) ≥ 90%
- [ ] Coverage web (shared-web) ≥ 80%
- [ ] Coverage infrastructure (shared-infrastructure) ≥ 70%
- [ ] Mutation score ≥ 70% (PIT)
- [ ] Tất cả rate limit / fail-open / fail-closed được test
- [ ] Tất cả container failure scenario được test (stop container giữa test)
- [ ] Security test pass (log injection, XSS, path traversal, JWT tampering)
- [ ] i18n test pass (vi, en, fallback)
- [ ] Recovery scenario có test (container restart, network blip)
- [ ] Mỗi test có tag chuẩn (Right-BICEP / CORRECT) trong table
- [ ] Không có business module nào phải fix sau khi shared test pass

---

## 13. Tham chiếu nhanh

| Thông tin | Vị trí |
|-----------|--------|
| Error codes | `shared-kernel/exception/ErrorCode.java` |
| Exception classes | `shared-kernel/exception/*.java` |
| DTO classes | `shared-kernel/dto/*.java` |
| Web filter/resolver | `shared-web/**/*.java` |
| Infrastructure adapters | `shared-infrastructure/**/*.java` |
| Test generation rules | `TEST_GENERATION_RULES.md` |
| Test methodology | First + Right-BICEP + CORRECT |

---

## 14. Tham chiếu chuẩn

| Chuẩn | Tác giả | Năm | Áp dụng |
|-------|---------|-----|---------|
| Test Pyramid | Martin Fowler | 2012 | Toàn bộ file |
| FIRST | Tim Ottinger | 2009 | §1.2 |
| Right-BICEP | Brian Marick | 1995 | §1.3, mỗi test |
| CORRECT | IEEE 829 / Andy Hunt | 1999 | §1.4, mỗi test |
| AAA Pattern | Bill Wake | 2003 | TEST_GENERATION_RULES §3.3 |