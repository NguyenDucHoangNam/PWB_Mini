package com.pwb.audio.application.command;

/**
 * A voice tag clip handed in by the client, free of any web framework type so the application layer does
 * not depend on Spring's multipart abstraction.
 *
 * <p>The bytes are held in memory on purpose: the clip is capped at a few seconds, and both the duration
 * probe and the storage upload need to read it, so streaming twice would buy nothing.
 */
public record VoiceTagAudioUpload(
        String fileName,
        String contentType,
        byte[] content
) {

    public long sizeBytes() {
        return content == null ? 0L : content.length;
    }
}
