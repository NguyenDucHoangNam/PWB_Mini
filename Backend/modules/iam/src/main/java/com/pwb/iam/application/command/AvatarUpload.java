package com.pwb.iam.application.command;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public record AvatarUpload(
        String originalFilename,
        String contentType,
        long size,
        byte[] header,
        StreamSupplier streamSupplier
) {

    public static final int HEADER_BYTES = 12;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};

    @FunctionalInterface
    public interface StreamSupplier {
        InputStream open() throws IOException;
    }

    public AvatarUpload {
        if (streamSupplier == null) {
            throw new IllegalArgumentException("streamSupplier must not be null");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("file must not be empty");
        }
        header = header == null ? new byte[0] : header.clone();
    }

    public InputStream openStream() throws IOException {
        return streamSupplier.open();
    }

    @Override
    public byte[] header() {
        return header.clone();
    }

    public boolean hasMagicBytesFor(String declaredContentType) {
        if (declaredContentType == null) {
            return false;
        }
        return switch (declaredContentType) {
            case "image/jpeg" -> startsWith(JPEG_MAGIC, 0);
            case "image/png" -> startsWith(PNG_MAGIC, 0);
            case "image/webp" -> startsWith(RIFF_MAGIC, 0) && startsWith(WEBP_MAGIC, 8);
            default -> false;
        };
    }

    private boolean startsWith(byte[] magic, int offset) {
        if (header.length < offset + magic.length) {
            return false;
        }
        return Arrays.equals(header, offset, offset + magic.length, magic, 0, magic.length);
    }

    public static AvatarUpload of(String originalFilename, String contentType, long size, byte[] content) {
        byte[] head = Arrays.copyOf(content, Math.min(HEADER_BYTES, content.length));
        return new AvatarUpload(originalFilename, contentType, size, head, () -> new ByteArrayInputStream(content));
    }
}
