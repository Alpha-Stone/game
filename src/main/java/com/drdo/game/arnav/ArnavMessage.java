//package com.drdo.game.arnav;
//
//import lombok.AllArgsConstructor;
//import org.apache.kafka.clients.consumer.ConsumerConfig;
//import org.apache.kafka.clients.consumer.ConsumerRecord;
//import org.apache.kafka.clients.consumer.ConsumerRecords;
//import org.apache.kafka.clients.consumer.KafkaConsumer;
//import org.apache.kafka.common.errors.WakeupException;
//import org.apache.kafka.common.serialization.StringDeserializer;
//import org.springframework.stereotype.Service;
//import org.springframework.util.ObjectUtils;
//
//import java.time.Duration;
//import java.util.Collections;
//import java.util.Properties;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//@Service
//@AllArgsConstructor
//public class ArnavMessage implements Runnable{
//
//    private final String topicName;
//    private final String bootstrapServers;
//    private final String groupId;
//
//    private final AtomicBoolean running = new AtomicBoolean(false);
//    private volatile KafkaConsumer<String, String> consumer;
//
//    @Override
//    public void run() {
//        Properties props = new Properties();
//        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
//        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//
//        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
//        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
//
//        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "50");
//
//        consumer = new KafkaConsumer<>(props);
//
//        try {
//            consumer.subscribe(Collections.singletonList(topicName));
//
//            while (running.get()){
//                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
//
//                for (ConsumerRecord<String, String> record : records){
//                    try {
//                        handleMessage(record);
//                    } catch (Exception e) {
//                        handleProcessingError(record, e);
//                    }
//                }
//
//                try {
//                    consumer.commitSync();
//                } catch (Exception e){
//                    // commit failure handling — log, don't crash the loop
//                }
//            }
//        }
//        catch (WakeupException e){
//            if (running.get()){
//                throw e;
//            }
//        }
//
//        finally {
//            try {
//                consumer.close();
//            }
//            catch (Exception ignored){}
//        }
//    }
//
//    private void handleProcessingError(ConsumerRecord<String, String> record, Exception e) {
//
//    }
//
//    private void handleMessage(ConsumerRecord<String, String> record) {
//        String text = record.value();
//    }
//
//    public void shutdown(){
//        running.set(false);
//        if (consumer != null) consumer.wakeup();
//    }
//}
