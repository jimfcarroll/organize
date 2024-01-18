import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class KafkaReplayProducer {

    public static void main(String[] args) {
        String bootstrapServers = "YOUR_BOOTSTRAP_SERVERS"; // Replace with your Kafka bootstrap servers
        String topic = "YOUR_TOPIC"; // Replace with your topic

        // Kafka producer properties
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        // Create Kafka producer
        KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(properties);

        // Assume these are your captured payload and headers
        byte[] payload = ...; // Your captured payload
        Map<String, byte[]> headersMap = ...; // Your captured headers

        // Convert headers to Kafka Header format
        List<Header> headers = new ArrayList<>();
        headersMap.forEach((key, value) -> headers.add(new RecordHeader(key, value)));

        // Create a producer record with the same payload and headers
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic, null, null, payload, headers);

        // Send the message
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                // Handle the exception
                exception.printStackTrace();
            } else {
                // Message was sent successfully
                System.out.println("Message sent successfully: " + metadata);
            }
        });

        // Close the producer
        producer.close();
    }
}
