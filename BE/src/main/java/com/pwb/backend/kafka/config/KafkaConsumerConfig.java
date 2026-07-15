package com.pwb.backend.kafka.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Value("${app.notification.retry.initial-interval-ms:1000}")
    private long notificationInitialIntervalMs;

    @Value("${app.notification.retry.multiplier:2.0}")
    private double notificationMultiplier;

    @Value("${app.notification.retry.max-interval-ms:16000}")
    private long notificationMaxIntervalMs;

    @Value("${app.notification.retry.max-attempts:5}")
    private int notificationMaxAttempts;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3)));

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> notificationKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(2);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                        KafkaTopicConfig.NOTIFICATION_DLQ, record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(notificationInitialIntervalMs);
        backOff.setMultiplier(notificationMultiplier);
        backOff.setMaxInterval(notificationMaxIntervalMs);
        backOff.setMaxElapsedTime(computeMaxElapsedTime(notificationMaxAttempts));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setCommitRecovered(true);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    private long computeMaxElapsedTime(int maxAttempts) {
        long interval = Math.max(100L, notificationInitialIntervalMs);
        long max = Math.max(interval, notificationMaxIntervalMs);
        long total = 0L;
        for (int i = 0; i < maxAttempts - 1; i++) {
            total += interval;
            interval = Math.min((long) (interval * notificationMultiplier), max);
        }
        return total;
    }
}
