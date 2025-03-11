package cs6650.hw1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class SkierConsumer {
  private static final String QUEUE_NAME = "ski_lift_rides";
  private static final ConcurrentHashMap<Integer, Integer> skierLifts = new ConcurrentHashMap<>();
  private static final int THREAD_COUNT = 8;

  public static void main(String[] args) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("44.232.3.127");
    factory.setPort(5672);
    factory.setUsername("admin");
    factory.setPassword("admin123");

    Connection connection = factory.newConnection();
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

    for (int i = 0; i < THREAD_COUNT; i++) {
      executor.submit(() -> {
        try {
          Channel channel = connection.createChannel();
          channel.queueDeclare(QUEUE_NAME, true, false, false, null);
          channel.basicQos(50);

          DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            int skierID = json.get("skierID").getAsInt();
            skierLifts.merge(skierID, 1, Integer::sum);
            System.out.println(Thread.currentThread().getName() + " Received: " + message);
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
          };

          channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    }

    while (true) {
      Thread.sleep(60000);
    }
  }
}
