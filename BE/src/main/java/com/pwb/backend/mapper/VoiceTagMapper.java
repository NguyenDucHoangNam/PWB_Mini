package com.pwb.backend.mapper;

import com.pwb.backend.dto.response.VoiceOption;
import com.pwb.backend.dto.response.VoiceTagResponse;
import com.pwb.backend.entity.rdbms.VoiceTag;
import com.pwb.backend.enums.VoiceLanguage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = {})
public interface VoiceTagMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    @Mapping(target = "textContent", source = "textContent")
    @Mapping(target = "languageCode", source = "languageCode")
    @Mapping(target = "voiceName", source = "voiceName")
    @Mapping(target = "isDefault", source = "default")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantToString")
    VoiceTagResponse toResponse(VoiceTag voiceTag);

    List<VoiceTagResponse> toResponseList(List<VoiceTag> voiceTags);

    default VoiceOption toVoiceOption(VoiceLanguage.VoiceOption option) {
        return VoiceOption.builder()
                .voiceName(option.voiceName())
                .gender(option.gender())
                .build();
    }

    default List<VoiceOption> toVoiceOptions(List<VoiceLanguage.VoiceOption> options) {
        if (options == null) {
            return List.of();
        }
        return options.stream().map(this::toVoiceOption).toList();
    }

    @org.mapstruct.Named("uuidToString")
    default String uuidToString(java.util.UUID id) {
        return id == null ? null : id.toString();
    }

    @org.mapstruct.Named("instantToString")
    default String instantToString(java.time.Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
