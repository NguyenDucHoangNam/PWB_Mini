package com.pwb.backend.config;

import com.pwb.backend.filter.RateLimitRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private long defaultPermitsPerWindow = 100;
    private Duration defaultWindow = Duration.ofMinutes(1);
    private List<RateLimitRule> rules = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getDefaultPermitsPerWindow() {
        return defaultPermitsPerWindow;
    }

    public void setDefaultPermitsPerWindow(long defaultPermitsPerWindow) {
        this.defaultPermitsPerWindow = defaultPermitsPerWindow;
    }

    public Duration getDefaultWindow() {
        return defaultWindow;
    }

    public void setDefaultWindow(Duration defaultWindow) {
        this.defaultWindow = defaultWindow;
    }

    public List<RateLimitRule> getRules() {
        return rules;
    }

    public void setRules(List<RateLimitRule> rules) {
        this.rules = rules;
    }
}