package com.pwb.audio.application.support;

import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.model.VoiceTag;

/**
 * Shared by the ordinary listing and by search so both answer with identical rows.
 */
public final class VoiceTagViews {

    private VoiceTagViews() {
    }

    public static VoiceTagView toView(VoiceTag voiceTag) {
        return new VoiceTagView(
                voiceTag.getId(),
                voiceTag.getUserId(),
                voiceTag.getName(),
                voiceTag.getTagType(),
                voiceTag.getSourceText(),
                voiceTag.getLanguageCode(),
                voiceTag.getVoiceName(),
                voiceTag.getDurationSeconds(),
                voiceTag.getFileSizeBytes(),
                voiceTag.isDefault(),
                voiceTag.getCreatedAt(),
                voiceTag.getUpdatedAt()
        );
    }
}
