package com.pwb.backend.common.config;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Subdivision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

@Configuration
@Slf4j
public class GeoIpConfig {

    private static final String DATABASE_RESOURCE = "geoip/GeoLite2-City.mmdb";

    @Bean
    public DatabaseReader geoIpDatabaseReader() throws IOException {
        ClassPathResource resource = new ClassPathResource(DATABASE_RESOURCE);
        if (!resource.exists()) {
            log.warn("GeoIP database not found on classpath: {}. Geolocation features disabled.", DATABASE_RESOURCE);
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            byte[] payload = in.readAllBytes();
            log.info("Loaded GeoIP database ({} bytes) into memory", payload.length);
            return new DatabaseReader.Builder(new java.io.ByteArrayInputStream(payload)).build();
        }
    }

    @Bean
    public DisposableBean geoIpShutdownHook(DatabaseReader geoIpDatabaseReader) {
        return () -> {
            if (geoIpDatabaseReader != null) {
                try {
                    geoIpDatabaseReader.close();
                } catch (IOException ex) {
                    log.warn("Failed to close GeoIP database reader: {}", ex.getMessage());
                }
            }
        };
    }

    public static GeoLocation resolve(DatabaseReader reader, String ip) {
        if (reader == null || ip == null || ip.isBlank()) {
            return GeoLocation.unknown();
        }
        try {
            CityResponse response = reader.city(InetAddress.getByName(ip));
            Country country = response.getCountry();
            City city = response.getCity();
            Subdivision subdivision = response.getMostSpecificSubdivision();
            String countryName = country != null ? country.getName() : null;
            String cityName = city != null ? city.getName() : null;
            String subdivisionName = subdivision != null ? subdivision.getName() : null;
            return new GeoLocation(countryName, subdivisionName, cityName);
        } catch (Exception ex) {
            log.debug("GeoIP lookup failed for {}: {}", ip, ex.getMessage());
            return GeoLocation.unknown();
        }
    }

    public record GeoLocation(String country, String region, String city) {
        public static GeoLocation unknown() {
            return new GeoLocation(null, null, null);
        }

        public String display() {
            if (city != null && !city.isBlank()) {
                return city + (country != null ? ", " + country : "");
            }
            return country != null ? country : "Unknown";
        }
    }
}
