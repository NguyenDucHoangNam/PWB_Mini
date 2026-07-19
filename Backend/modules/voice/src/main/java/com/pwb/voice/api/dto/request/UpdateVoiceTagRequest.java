package com.pwb.voice.api.dto.request;

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
public class UpdateVoiceTagRequest {

    @Size(max = 128, message = "{validation.name.maxlength}")
    private String name;

    @Size(max = 512, message = "{validation.description.maxlength}")
    private String description;

    @Size(max = 4000, message = "{validation.text.maxlength}")
    private String text;

    @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$", message = "{validation.languagecode.pattern}")
    private String languageCode;
}
