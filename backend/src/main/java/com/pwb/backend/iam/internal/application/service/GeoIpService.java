package com.pwb.backend.iam.internal.application.service;

import com.maxmind.db.Reader.FileMode;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.pwb.backend.iam.internal.interfaces.config.IamProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoIpService {

  private enum State { UNINITIALIZED, LOADED, FALLBACK }

  private final IamProperties iamProperties;
  private final ResourceLoader resourceLoader;

  private final AtomicReference<State> state = new AtomicReference<>(State.UNINITIALIZED);
  private volatile DatabaseReader databaseReader;

  public String getLocation(String ipAddress) {
    if (isLocalAddress(ipAddress)) {
      return "Local / Unknown";
    }
    DatabaseReader reader = ensureReader();
    if (reader == null) {
      return "Unknown";
    }
    try {
      InetAddress ip = InetAddress.getByName(ipAddress);
      CityResponse response = reader.city(ip);
      if (response == null) {
        return "Unknown";
      }
      String cityName = response.getCity() != null ? response.getCity().getName() : null;
      String countryName = response.getCountry() != null ? response.getCountry().getName() : null;

      if (cityName != null && !cityName.isEmpty()) {
        return cityName + ", " + countryName;
      }
      return countryName != null ? countryName : "Unknown";
    } catch (Exception e) {
      log.debug("Failed to lookup location for IP: {}", ipAddress, e);
      return "Unknown";
    }
  }

  private DatabaseReader ensureReader() {
    State current = state.get();
    if (current == State.LOADED) {
      return databaseReader;
    }
    if (current == State.FALLBACK) {
      return null;
    }
    synchronized (this) {
      if (state.get() == State.UNINITIALIZED) {
        loadDatabase();
      }
    }
    return state.get() == State.LOADED ? databaseReader : null;
  }

  private void loadDatabase() {
    String dbPath = iamProperties.getGeoIp().getDatabasePath();
    Resource resource = resourceLoader.getResource(dbPath);
    if (!resource.exists()) {
      log.warn("GeoIP database file not found at: {}. Running in fallback mode.", dbPath);
      state.set(State.FALLBACK);
      return;
    }
    try (InputStream inputStream = resource.getInputStream()) {
      databaseReader = new DatabaseReader.Builder(inputStream)
          .fileMode(FileMode.MEMORY)
          .build();
      state.set(State.LOADED);
      log.info("GeoIP database loaded lazily on first lookup.");
    } catch (Exception e) {
      log.warn("Failed to load GeoIP database. Running in fallback mode.", e);
      state.set(State.FALLBACK);
    }
  }

  private boolean isLocalAddress(String ipAddress) {
    try {
      java.net.InetAddress addr = java.net.InetAddress.getByName(ipAddress);
      return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
    } catch (Exception e) {
      return false;
    }
  }

  @PreDestroy
  public void destroy() {
    if (databaseReader != null) {
      try {
        databaseReader.close();
      } catch (Exception e) {
        log.error("Failed to close GeoIP database reader", e);
      }
    }
  }
}
