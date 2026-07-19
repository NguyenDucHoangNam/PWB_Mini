package com.pwb.voice.api.dto.request;

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
public class CreateTtsVoiceTagRequest {

    @NotBlank(message = "{validation.name.required}")
    @Size(max = 128, message = "{validation.name.maxlength}")
    private String name;

    @Size(max = 512, message = "{validation.description.maxlength}")
    private String description;

    @NotBlank(message = "{validation.text.required}")
    @Size(max = 4000, message = "{validation.text.maxlength}")
    private String text;

    @NotBlank(message = "{validation.languagecode.required}")
    @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$", message = "{validation.languagecode.pattern}")
    private String languageCode;
}
