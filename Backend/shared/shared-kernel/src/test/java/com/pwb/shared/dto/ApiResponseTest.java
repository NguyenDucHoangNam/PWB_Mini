package com.pwb.shared.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse — generic API response wrapper")
class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("should_wrap_data_with_success_true_when_success_factory_used")
    void should_wrap_data_with_success_true_when_success_factory_used() {
        Map<String, Object> data = Map.of("userId", "abc-123");

        ApiResponse<Map<String, Object>> response = ApiResponse.success(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(data);
        assertThat(response.getError()).isNull();
        assertThat(response.getCode()).isNull();
        assertThat(response.getMessage()).isNull();
    }

    @Test
    @DisplayName("should_wrap_data_with_message_when_success_factory_with_message_used")
    void should_wrap_data_with_message_when_success_factory_with_message_used() {
        ApiResponse<String> response = ApiResponse.success("OK", "payload");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getMessage()).isEqualTo("OK");
    }

    @Test
    @DisplayName("should_carry_trace_id_and_timestamp_when_success_factory_used")
    void should_carry_trace_id_and_timestamp_when_success_factory_used() {
        long before = System.currentTimeMillis();

        ApiResponse<Void> response = ApiResponse.success(null);

        assertThat(response.getTraceId()).isNotBlank();
        assertThat(response.getTimestamp()).isGreaterThanOrEqualTo(before);
    }

    @Test
    @DisplayName("should_have_unique_trace_id_per_call")
    void should_have_unique_trace_id_per_call() {
        ApiResponse<Void> first = ApiResponse.success(null);
        ApiResponse<Void> second = ApiResponse.success(null);

        assertThat(first.getTraceId()).isNotEqualTo(second.getTraceId());
    }

    @Test
    @DisplayName("should_wrap_error_with_success_false_when_error_factory_used")
    void should_wrap_error_with_success_false_when_error_factory_used() {
        ApiResponse<Void> response = ApiResponse.error("USER_NOT_FOUND", "User does not exist");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getCode()).isEqualTo("USER_NOT_FOUND");
        assertThat(response.getMessage()).isEqualTo("User does not exist");
        assertThat(response.getError()).isNull();
    }

    @Test
    @DisplayName("should_carry_error_details_when_error_factory_with_details_used")
    void should_carry_error_details_when_error_factory_with_details_used() {
        Map<String, Object> details = Map.of("field", "email", "violations", 2);

        ApiResponse<Void> response = ApiResponse.error("VALIDATION_FAILED", "Bad request", details);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getMessage()).isEqualTo("Bad request");
        assertThat(response.getError()).isSameAs(details);
    }

    @Test
    @DisplayName("should_serialize_to_json_and_deserialize_back")
    void should_serialize_to_json_and_deserialize_back() throws Exception {
        ApiResponse<String> original = ApiResponse.success("OK", "hello");

        String json = objectMapper.writeValueAsString(original);
        ApiResponse<String> restored = objectMapper.readValue(json, ApiResponse.class);

        assertThat(restored.isSuccess()).isTrue();
        assertThat(restored.getData()).isEqualTo("hello");
        assertThat(restored.getMessage()).isEqualTo("OK");
        assertThat(restored.getTraceId()).isEqualTo(original.getTraceId());
    }

    @Test
    @DisplayName("should_default_data_to_null_when_no_args_constructor_used")
    void should_default_data_to_null_when_no_args_constructor_used() {
        ApiResponse<String> response = new ApiResponse<>();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
    }
}