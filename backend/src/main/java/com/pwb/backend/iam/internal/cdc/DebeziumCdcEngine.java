package com.pwb.backend.iam.internal.cdc;

import io.debezium.config.Configuration;
import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Profile;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DebeziumCdcEngine {

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final DebeziumOutboxEventHandler eventHandler;
  private final Environment environment;
  private DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine;

  public Configuration debeziumConnectorConfig() {
    String offsetPath = environment.getProperty(
        "app.cdc.offset-storage-path", "data/offsets.dat");
    File offsetFile = new File(offsetPath);

    String jdbcUrl = environment.getRequiredProperty("spring.datasource.url");
    if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
      throw new IllegalArgumentException("Unsupported spring.datasource.url format. Must be a postgresql jdbc url.");
    }
    String cleanUrl = jdbcUrl.substring(5);
    java.net.URI uri = java.net.URI.create(cleanUrl);
    String host = uri.getHost();
    int port = uri.getPort();
    if (port == -1) {
      port = 5432;
    }
    String path = uri.getPath();
    String dbname = path.startsWith("/") ? path.substring(1) : path;
    if (dbname.contains("?")) {
      dbname = dbname.substring(0, dbname.indexOf('?'));
    }

    return Configuration.create()
        .with("name", "pwb-postgres-connector")
        .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
        .with("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
        .with("offset.storage.file.filename", offsetFile.getAbsolutePath())
        .with("offset.flush.interval.ms", "60000")
        .with("database.hostname", host)
        .with("database.port", String.valueOf(port))
        .with("database.user", environment.getRequiredProperty("spring.datasource.username"))
        .with("database.password", environment.getRequiredProperty("spring.datasource.password"))
        .with("database.dbname", dbname)
        .with("topic.prefix", "pwb-cdc")
        .with("table.include.list", "public.outbox_events")
        .with("plugin.name", "pgoutput")
        .with("bootstrap.servers",
            environment.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"))
        .build();
  }

  @PostConstruct
  public void start() {
    this.debeziumEngine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
        .using(debeziumConnectorConfig().asProperties())
        .notifying(eventHandler::handleEvent)
        .build();

    log.info("Starting Debezium CDC Engine...");
    executor.execute(debeziumEngine);
  }

  @PreDestroy
  public void stop() throws IOException {
    if (this.debeziumEngine != null) {
      log.info("Stopping Debezium CDC Engine...");
      this.debeziumEngine.close();
    }
    executor.shutdown();
  }
}
