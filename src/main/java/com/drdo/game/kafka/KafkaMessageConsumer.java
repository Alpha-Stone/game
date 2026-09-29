package com.drdo.game.kafka;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

@Component
public class KafkaMessageConsumer {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.topic}")
    private String topic;

    @Value("${kafka.consumer.group-id}")
    private String groupId;

    private KafkaConsumer<String, String> consumer;

    private volatile boolean running = true;

    @PostConstruct
    public void init() {

        Properties properties = new Properties();

        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(properties);

        consumer.subscribe(Collections.singletonList(topic));

        startConsumer();
    }

    private void startConsumer() {

        Thread consumerThread = new Thread(() -> {

            while (running) {

                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {

                    System.out.println(
                            "Message received:"
                                    + " topic=" + record.topic()
                                    + " partition=" + record.partition()
                                    + " offset=" + record.offset()
                                    + " key=" + record.key()
                                    + " value=" + record.value()
                                    + " headers=" + record.headers()
                    );
                }
            }

        });

        consumerThread.start();
    }

    @PreDestroy
    public void close() {

        running = false;

        if (consumer != null) {
            consumer.wakeup();
            consumer.close();
        }
    }

}
