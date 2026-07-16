package com.pwb.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVoiceTagRequest {

    @NotBlank
    @Size(max = 250)
    private String textContent;

    @NotBlank
    @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$", message = "{validation.voice_tag.language_code}")
    private String languageCode;

    @NotBlank
    @Size(max = 100)
    private String voiceName;
}
