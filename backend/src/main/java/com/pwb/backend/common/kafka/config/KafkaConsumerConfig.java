package com.pwb.backend.common.kafka.config;

import com.pwb.backend.common.kafka.constant.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Bean
    @ConditionalOnMissingBean(name = "defaultConsumerFactory")
    public ConsumerFactory<String, String> defaultConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupIdPrefix());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaProperties.getConsumer().getMaxPollRecords());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    @ConditionalOnMissingBean(name = "defaultKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> defaultKafkaListenerContainerFactory(
            ConsumerFactory<String, String> defaultConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(defaultConsumerFactory);
        factory.setConcurrency(kafkaProperties.getConsumer().getConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + ".dlq", record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(
                kafkaProperties.getRetry().getInitialIntervalMs(),
                kafkaProperties.getRetry().getMultiplier());
        backOff.setMaxInterval(kafkaProperties.getRetry().getMaxIntervalMs());
        backOff.setMaxElapsedTime(kafkaProperties.getRetry().getMaxAttempts()
                * kafkaProperties.getRetry().getMaxIntervalMs());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public NewTopic iamUserRegisteredTopic() {
        return TopicBuilder.name(KafkaTopics.IAM_USER_REGISTERED)
                .partitions(kafkaProperties.getPartitions())
                .replicas(kafkaProperties.getReplicas())
                .build();
    }

    @Bean
    public NewTopic iamOtpResentTopic() {
        return TopicBuilder.name(KafkaTopics.IAM_OTP_RESENT)
                .partitions(kafkaProperties.getPartitions())
                .replicas(kafkaProperties.getReplicas())
                .build();
    }

    @Bean
    public NewTopic iamPasswordResetTopic() {
        return TopicBuilder.name(KafkaTopics.IAM_PASSWORD_RESET)
                .partitions(kafkaProperties.getPartitions())
                .replicas(kafkaProperties.getReplicas())
                .build();
    }

    @Bean
    public NewTopic iamAccountDeletionTopic() {
        return TopicBuilder.name(KafkaTopics.IAM_ACCOUNT_DELETION)
                .partitions(kafkaProperties.getPartitions())
                .replicas(kafkaProperties.getReplicas())
                .build();
    }

    @Bean
    public NewTopic iamAccountEventsTopic() {
        return TopicBuilder.name(KafkaTopics.IAM_ACCOUNT_EVENTS)
                .partitions(kafkaProperties.getPartitions())
                .replicas(kafkaProperties.getReplicas())
                .build();
    }

    @Bean
    public NewTopic audioProcessingEventsTopic() {
        return TopicBuilder.name(KafkaTopics.AUDIO_PROCESSING_EVENTS)
                .partitions(kafkaProperties.getPartitions())
                .replicas(kafkaProperties.getReplicas())
                .build();
    }

    @Bean
    public NewTopic audioProcessingEventsDlqTopic() {
        return TopicBuilder.name(KafkaTopics.AUDIO_PROCESSING_EVENTS_DLQ)
                .partitions(kafkaProperties.getPartitions())
                .replicas(kafkaProperties.getReplicas())
                .build();
    }

    @Bean
    public NewTopic audioShareEmailTopic() {
        return TopicBuilder.name(KafkaTopics.AUDIO_SHARE_EMAIL)
                .partitions(kafkaProperties.getPartitions())
                .replicas(kafkaProperties.getReplicas())
                .build();
    }

    @Bean
    public NewTopic liveroomLifecycleTopic() {
        return TopicBuilder.name(KafkaTopics.LIVEROOM_LIFECYCLE)
                .partitions(kafkaProperties.getPartitions())
                .replicas(kafkaProperties.getReplicas())
                .build();
    }
}
