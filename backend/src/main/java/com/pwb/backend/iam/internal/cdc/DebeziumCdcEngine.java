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
  private DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine;
 
  public Configuration debeziumConnectorConfig() {
    File offsetFile = new File("target/offsets.dat");
    return Configuration.create()
        .with("name", "pwb-postgres-connector")
        .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
        .with("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
        .with("offset.storage.file.filename", offsetFile.getAbsolutePath())
        .with("offset.flush.interval.ms", "60000")
        .with("database.hostname", "localhost")
        .with("database.port", "5433")
        .with("database.user", "pwb_user")
        .with("database.password", "pwb_password")
        .with("database.dbname", "pwb_db")
        .with("topic.prefix", "pwb-cdc")
        .with("table.include.list", "public.outbox_events")
        .with("plugin.name", "pgoutput")
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
