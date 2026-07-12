package com.pwb.backend.modules.voice_tag.config;

import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;

@Slf4j
@Configuration
public class VoiceTagGcpConfig {

    @Bean(destroyMethod = "close")
    @Lazy
    public TextToSpeechClient textToSpeechClient() throws IOException {
        log.info("Initializing Google Cloud Text-to-Speech client with Application Default Credentials");
        return TextToSpeechClient.create();
    }
}
