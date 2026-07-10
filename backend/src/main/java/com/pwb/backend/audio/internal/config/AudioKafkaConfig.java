package com.pwb.backend.audio.internal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AudioKafkaConfig {

  private final String bootstrapServers;
  private final int topicPartitions;
  private final int topicReplicas;
  private final ObjectMapper objectMapper;

  public AudioKafkaConfig(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
      @Value("${spring.kafka.topic.partitions:1}") int topicPartitions,
      @Value("${spring.kafka.topic.replicas:1}") int topicReplicas,
      ObjectMapper objectMapper) {
    this.bootstrapServers = bootstrapServers;
    this.topicPartitions = topicPartitions;
    this.topicReplicas = topicReplicas;
    this.objectMapper = objectMapper;
  }

  @Bean
  public NewTopic audioProcessingEventsTopic() {
    return TopicBuilder.name("audio-processing-events")
        .partitions(topicPartitions)
        .replicas(topicReplicas)
        .build();
  }

  @Bean
  public NewTopic audioProcessingEventsDlqTopic() {
    return TopicBuilder.name("audio-processing-events-dlq")
        .partitions(topicPartitions)
        .replicas(topicReplicas)
        .build();
  }

  @Bean
  public ProducerFactory<String, Object> audioProcessingProducerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10000);
    configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
    configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, Object> audioProcessingKafkaTemplate() {
    KafkaTemplate<String, Object> template = new KafkaTemplate<>(audioProcessingProducerFactory());
    template.setDefaultTopic("audio-processing-events");
    return template;
  }
}
