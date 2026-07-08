package com.pwb.backend.iam.internal.service;

import com.pwb.backend.iam.internal.config.IamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeoIpServiceTest {

  private IamProperties properties;
  private ResourceLoader resourceLoader;

  @BeforeEach
  void setUp() {
    properties = new IamProperties();
    properties.getGeoip().setDatabasePath("classpath:GeoLite2-City.mmdb");
    resourceLoader = mock(ResourceLoader.class);
  }

  @Test
  void getLocation_localIp_returnsLocalUnknown() throws IOException {
    Resource missing = mock(Resource.class);
    when(missing.exists()).thenReturn(false);
    when(resourceLoader.getResource("classpath:GeoLite2-City.mmdb")).thenReturn(missing);
    GeoIpService svc = new GeoIpService(properties, resourceLoader);
    svc.init();

    String result = svc.getLocation("127.0.0.1");
    assertEquals("Local / Unknown", result);
  }

  @Test
  void getLocation_missingDatabase_runsInFallbackMode() throws IOException {
    Resource missing = mock(Resource.class);
    when(missing.exists()).thenReturn(false);
    when(resourceLoader.getResource("classpath:GeoLite2-City.mmdb")).thenReturn(missing);
    GeoIpService svc = new GeoIpService(properties, resourceLoader);
    svc.init();

    String result = svc.getLocation("203.0.113.10");
    assertEquals("Local / Unknown", result);
  }

  @Test
  void getLocation_invalidIp_returnsLocalOrUnknown() throws IOException {
    Resource empty = mock(Resource.class);
    when(empty.exists()).thenReturn(true);
    ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
    when(empty.getInputStream()).thenReturn(stream);
    when(resourceLoader.getResource("classpath:GeoLite2-City.mmdb")).thenReturn(empty);
    GeoIpService svc = new GeoIpService(properties, resourceLoader);
    svc.init();

    String result = svc.getLocation("not-a-valid-ip");
    assertTrue(result.equals("Unknown") || result.equals("Local / Unknown"));
  }

  @Test
  void getLocation_databaseFailsToOpen_runsInFallbackMode() throws IOException {
    Resource broken = mock(Resource.class);
    when(broken.exists()).thenReturn(true);
    when(broken.getInputStream()).thenThrow(new IOException("corrupt"));
    when(resourceLoader.getResource("classpath:GeoLite2-City.mmdb")).thenReturn(broken);
    GeoIpService svc = new GeoIpService(properties, resourceLoader);
    svc.init();

    assertEquals("Local / Unknown", svc.getLocation("203.0.113.10"));
  }
}