package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param sizeBytes the exact size of the file about to be uploaded. Required because it is signed into
 *                  the returned URL: storage then refuses a body of any other size, which is the only
 *                  point at which an upload can be bounded before its bytes have been accepted and paid
 *                  for. A client that understates it cannot then upload more — the upload simply fails.
 */
public record UploadUrlRequest(
        @NotBlank @Size(max = 16) String format,
        @NotNull @Positive Long sizeBytes
) {
}
