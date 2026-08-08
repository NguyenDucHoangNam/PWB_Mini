package com.pwb.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UuidGenerator — UUID utility")
class UuidGeneratorTest {

    @Test
    @DisplayName("should_return_unique_id_per_call")
    void should_return_unique_id_per_call() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(UuidGenerator.generate());
        }

        assertThat(ids).hasSize(1000);
    }

    @Test
    @DisplayName("should_return_valid_uuid_string_format")
    void should_return_valid_uuid_string_format() {
        String id = UuidGenerator.generate();

        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(id).hasSize(36);
    }

    @Test
    @DisplayName("should_return_id_without_hyphens_when_called_without_hyphens")
    void should_return_id_without_hyphens_when_called_without_hyphens() {
        String id = UuidGenerator.generateWithoutHyphens();

        assertThat(id).doesNotContain("-");
        assertThat(id).hasSize(32);
    }

    @Test
    @DisplayName("should_strip_hyphens_from_generated_uuid")
    void should_strip_hyphens_from_generated_uuid() {
        String id = UuidGenerator.generateWithoutHyphens();

        String withHyphens = id.substring(0, 8) + "-" + id.substring(8, 12) + "-"
                + id.substring(12, 16) + "-" + id.substring(16, 20) + "-"
                + id.substring(20, 32);
        assertThat(UUID.fromString(withHyphens)).isNotNull();
    }
}