package com.pwb.voice.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadVoiceTagRequest {

    @NotBlank(message = "{validation.name.required}")
    @Size(max = 128, message = "{validation.name.maxlength}")
    private String name;
}