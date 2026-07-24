package com.pwb.liveroom.infrastructure.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Component
public class PlaybackMetrics {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    public void increment(String name) {
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
        log.debug("playback.metric name={} count={}", name, counters.get(name).sum());
    }

    public long count(String name) {
        LongAdder adder = counters.get(name);
        return adder == null ? 0L : adder.sum();
    }
}