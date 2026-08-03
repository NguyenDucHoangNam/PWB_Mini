package com.pwb.audio.infrastructure.audio;

import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.infrastructure.audio.properties.AudioProcessorProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioWorkspace {

    private static final String JOB_DIR_PREFIX = "job-";

    private final AudioProcessorProperties properties;

    @PostConstruct
    void sweepOrphans() {
        Path root = root();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            log.error("Cannot create audio working directory: {}", root, ex);
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofHours(properties.getOrphanRetentionHours()));
        try (Stream<Path> entries = Files.list(root)) {
            int removed = 0;
            for (Path entry : entries.filter(AudioWorkspace::isJobDirectory).toList()) {
                if (Files.getLastModifiedTime(entry).toInstant().isBefore(cutoff)) {
                    deleteRecursively(entry);
                    removed++;
                }
            }
            if (removed > 0) {
                log.warn("Removed {} orphaned audio job directories from {}", removed, root);
            }
        } catch (IOException ex) {
            log.error("Failed to sweep orphaned audio job directories in {}", root, ex);
        }
    }

    public Path createJobDirectory(UUID songId) {
        Path root = root();
        try {
            Files.createDirectories(root);
            return Files.createTempDirectory(root, JOB_DIR_PREFIX + songId + "-");
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, ex);
        }
    }

    public void assertSpaceAvailable(long requiredBytes) {
        try {
            FileStore store = Files.getFileStore(root());
            long usable = store.getUsableSpace();
            if (usable < requiredBytes) {
                log.error("Insufficient disk space in {}: need {} bytes, {} available",
                        root(), requiredBytes, usable);
                throw new AudioBusinessException(AudioErrorCode.INSUFFICIENT_DISK_SPACE);
            }
        } catch (IOException ex) {
            log.warn("Could not determine free disk space for {}", root(), ex);
        }
    }

    public void release(Path jobDirectory) {
        if (jobDirectory == null || !Boolean.TRUE.equals(properties.getCleanupTempFiles())) {
            return;
        }
        deleteRecursively(jobDirectory);
    }

    private Path root() {
        return Paths.get(properties.getWorkingDir()).toAbsolutePath().normalize();
    }

    private static boolean isJobDirectory(Path path) {
        return Files.isDirectory(path) && path.getFileName().toString().startsWith(JOB_DIR_PREFIX);
    }

    private void deleteRecursively(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("Could not delete temp file {}: {}", path, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.warn("Could not clean up job directory {}: {}", directory, ex.getMessage());
        }
    }
}
