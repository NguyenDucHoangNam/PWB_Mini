package com.pwb.backend.iam.internal.service;

import com.maxmind.db.Reader.FileMode;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.pwb.backend.iam.internal.config.IamProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.InetAddress;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoIpService {

  private final IamProperties iamProperties;
  private final ResourceLoader resourceLoader;

  private DatabaseReader databaseReader;
  private boolean fallbackMode = false;

  @PostConstruct
  public void init() {
    try {
      String dbPath = iamProperties.getGeoip().getDatabasePath();
      Resource resource = resourceLoader.getResource(dbPath);
      if (!resource.exists()) {
        log.warn("GeoIP database file not found at: {}. Running in fallback mode.", dbPath);
        fallbackMode = true;
        return;
      }
      try (InputStream inputStream = resource.getInputStream()) {
        databaseReader = new DatabaseReader.Builder(inputStream)
            .fileMode(FileMode.MEMORY)
            .build();
        log.info("GeoIP database loaded successfully.");
      }
    } catch (Exception e) {
      log.warn("Failed to load GeoIP database. Running in fallback mode.", e);
      fallbackMode = true;
    }
  }

  public String getLocation(String ipAddress) {
    if (fallbackMode || isLocalAddress(ipAddress)) {
      return "Local / Unknown";
    }
    try {
      InetAddress ip = InetAddress.getByName(ipAddress);
      CityResponse response = databaseReader.city(ip);
      String cityName = response.getCity().getName();
      String countryName = response.getCountry().getName();

      if (cityName != null && !cityName.isEmpty()) {
        return cityName + ", " + countryName;
      }
      return countryName != null ? countryName : "Unknown";
    } catch (Exception e) {
      log.debug("Failed to lookup location for IP: {}", ipAddress, e);
      return "Unknown";
    }
  }

  private boolean isLocalAddress(String ipAddress) {
    return "127.0.0.1".equals(ipAddress)
        || "0:0:0:0:0:0:0:1".equals(ipAddress)
        || ipAddress.startsWith("192.168.")
        || ipAddress.startsWith("10.")
        || ipAddress.startsWith("172.");
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
