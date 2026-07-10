package com.pwb.backend.shared.cdc;

import io.debezium.config.Configuration;
import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
@Component
@Profile("!test")
public class CdcEngine {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Environment environment;
    private final Consumer<RecordChangeEvent<SourceRecord>> eventConsumer;
    private final String connectorName;
    private final String tableIncludeList;

    private DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine;

    public CdcEngine(
        Environment environment,
        Consumer<RecordChangeEvent<SourceRecord>> eventConsumer,
        String connectorName,
        String tableIncludeList) {
        this.environment = environment;
        this.eventConsumer = eventConsumer;
        this.connectorName = connectorName;
        this.tableIncludeList = tableIncludeList;
    }

    public Configuration debeziumConnectorConfig() {
        String offsetPath = environment.getProperty(
            "app.cdc.offset-storage-path", "data/offsets-" + connectorName + ".dat");
        File offsetFile = new File(offsetPath);
        validateOffsetFileOwnership(offsetFile);

        String jdbcUrl = environment.getRequiredProperty("spring.datasource.url");
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Unsupported spring.datasource.url format. Must be a postgresql jdbc url.");
        }
        String cleanUrl = jdbcUrl.substring(5);
        URI uri = URI.create(cleanUrl);
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

        String topicPrefix = environment.getProperty("app.cdc.topic-prefix", "pwb-cdc");
        String pluginName = environment.getProperty("app.cdc.plugin-name", "pgoutput");
        String flushIntervalMs = environment.getProperty("app.cdc.offset-flush-interval-ms", "60000");

        return Configuration.create()
            .with("name", connectorName)
            .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
            .with("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
            .with("offset.storage.file.filename", offsetFile.getAbsolutePath())
            .with("offset.flush.interval.ms", flushIntervalMs)
            .with("database.hostname", host)
            .with("database.port", String.valueOf(port))
            .with("database.user", environment.getRequiredProperty("spring.datasource.username"))
            .with("database.password", environment.getRequiredProperty("spring.datasource.password"))
            .with("database.dbname", dbname)
            .with("topic.prefix", topicPrefix)
            .with("table.include.list", tableIncludeList)
            .with("plugin.name", pluginName)
            .with("bootstrap.servers",
                environment.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"))
            .build();
    }

    private void validateOffsetFileOwnership(File offsetFile) {
        if (!offsetFile.exists()) {
            return;
        }
        try (var reader = new java.io.BufferedReader(new java.io.FileReader(offsetFile))) {
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return;
            }

            int nameIdx = firstLine.indexOf("\"name\":\"");
            if (nameIdx < 0) {
                return;
            }
            int start = nameIdx + "\"name\":\"".length();
            int end = firstLine.indexOf('"', start);
            if (end < 0) {
                return;
            }
            String existingName = firstLine.substring(start, end);
            if (!connectorName.equals(existingName)) {
                throw new IllegalStateException(
                    "CDC offset file '" + offsetFile.getAbsolutePath()
                        + "' was previously used by connector '" + existingName
                        + "' but this engine is configured as '" + connectorName
                        + "'. Refusing to start to avoid cross-connector progress loss.");
            }
        } catch (IOException ex) {

            log.warn("Could not read CDC offset file {} for ownership check: {}",
                offsetFile.getAbsolutePath(), ex.getMessage());
        }
    }

    @PostConstruct
    public void start() {
        this.debeziumEngine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
            .using(debeziumConnectorConfig().asProperties())
            .notifying(eventConsumer)
            .build();

        log.info("Starting Debezium CDC Engine [{}]...", connectorName);
        executor.execute(debeziumEngine);
    }

    @PreDestroy
    public void stop() throws IOException {
        if (this.debeziumEngine != null) {
            log.info("Stopping Debezium CDC Engine [{}]...", connectorName);
            this.debeziumEngine.close();
        }
        executor.shutdown();
    }
}