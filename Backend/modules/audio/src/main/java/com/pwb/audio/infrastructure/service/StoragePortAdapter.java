package com.pwb.audio.infrastructure.service;

import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.domain.service.PresignedUrl;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.audio.domain.service.StoredObject;
import com.pwb.infra.storage.StorageService;
import com.pwb.infra.storage.dto.ObjectMetadata;
import com.pwb.infra.storage.dto.PresignedUrlResult;
import com.pwb.infra.storage.exception.StorageErrorCode;
import com.pwb.infra.storage.exception.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoragePortAdapter implements StoragePort {

    private final StorageService storageService;

    @Override
    public PresignedUrl presignDownload(String storageKey, Duration expiration) {
        return toPresignedUrl(storageService.generatePresignedUrl(storageKey, expiration));
    }

    @Override
    public PresignedUrl presignUpload(String storageKey, String contentType, long contentLength, Duration expiration) {
        return toPresignedUrl(
                storageService.generatePresignedUploadUrl(storageKey, contentType, contentLength, expiration));
    }

    @Override
    public byte[] readHead(String storageKey, int maxBytes) {
        try {
            return storageService.readHead(storageKey, maxBytes);
        } catch (StorageException ex) {
            if (ex.getErrorCode() == StorageErrorCode.STORAGE_OBJECT_NOT_FOUND) {
                return new byte[0];
            }
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
    }

    @Override
    public Optional<StoredObject> findMetadata(String storageKey) {
        try {
            ObjectMetadata metadata = storageService.getMetadata(storageKey);
            return Optional.of(new StoredObject(
                    metadata.getKey(),
                    metadata.getSizeBytes(),
                    metadata.getContentType()
            ));
        } catch (StorageException ex) {
            if (ex.getErrorCode() == StorageErrorCode.STORAGE_OBJECT_NOT_FOUND) {
                return Optional.empty();
            }
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
    }

    @Override
    public void copy(String sourceKey, String destinationKey) {
        try {
            storageService.copy(sourceKey, destinationKey);
        } catch (StorageException ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        storageService.delete(storageKey);
    }

    @Override
    public void downloadToPath(String storageKey, Path destination) {
        storageService.downloadToFile(storageKey, destination);
    }

    /**
     * {@code contentLength} is no longer passed on: the transfer manager reads it off the file, and
     * taking the caller's word for it is how a mismatch becomes a corrupt object.
     */
    @Override
    public String uploadFromPath(String storageKey, Path sourcePath, long contentLength) {
        String contentType;
        try {
            contentType = Files.probeContentType(sourcePath);
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
        return storageService.uploadFile(storageKey, sourcePath, contentType).getKey();
    }

    @Override
    public String uploadBytes(String storageKey, byte[] content, String contentType) {
        return storageService.upload(storageKey, content, contentType).getKey();
    }

    private PresignedUrl toPresignedUrl(PresignedUrlResult result) {
        return new PresignedUrl(result.getUrl(), result.getExpiresAt());
    }
}
