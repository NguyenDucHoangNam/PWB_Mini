package com.pwb.audio.domain.service;

import java.util.Optional;

/**
 * Remembers synthesised previews so replaying the same phrase does not buy the same audio twice.
 *
 * <p>Implementations must fail open. A cache that cannot be reached has to look like a miss rather than
 * an error: paying Google for a preview is a worse outcome than not caching it, but far better than
 * failing the request because a cache was down.
 */
public interface TtsPreviewCache {

    /** @return empty on a miss, and equally whenever the cache cannot be reached */
    Optional<TtsResult> find(TtsRequest request);

    /** Does nothing when the cache is unreachable, or when the payload is too large to be worth keeping. */
    void put(TtsRequest request, TtsResult result);
}
