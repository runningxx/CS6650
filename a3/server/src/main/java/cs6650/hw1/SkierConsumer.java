package cs6650.hw1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;
import software.amazon.awssdk.services.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class SkierConsumer {
  private static final String QUEUE_NAME = "ski_lift_rides";
  private static final int THREAD_COUNT = 8;

  public static void main(String[] args) throws Exception {
    // === Initialize RabbitMQ ===
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("44.232.3.127");
    factory.setPort(5672);
    factory.setUsername("admin");
    factory.setPassword("admin123");

    Connection rabbitConnection = factory.newConnection();
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

    // === Initialize DynamoDB ===
    DynamoDbClient ddb = DynamoDbClient.builder()
        .region(Region.US_WEST_2)  // ✅ 修改为你的 Region（例如 us-west-2）
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();

    for (int i = 0; i < THREAD_COUNT; i++) {
      executor.submit(() -> {
        try {
          Channel channel = rabbitConnection.createChannel();
          channel.queueDeclare(QUEUE_NAME, true, false, false, null);
          channel.basicQos(50);

          DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
              String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
              JsonObject json = JsonParser.parseString(message).getAsJsonObject();

              // === Extract data safely ===
              int skierID = json.get("skierID").getAsInt();
              int resortID = json.get("resortID").getAsInt();
              int seasonID = json.get("seasonID").getAsInt();
              int dayID = json.get("dayID").getAsInt();
              int liftID = json.get("liftID").getAsInt();
              int time = json.get("time").getAsInt();
              String daySeason = dayID + "#" + seasonID;

              // === Prepare item for DynamoDB ===
              Map<String, AttributeValue> item = new HashMap<>();
              item.put("SkierID", AttributeValue.fromN(String.valueOf(skierID)));
              item.put("DaySeason", AttributeValue.fromS(daySeason));
              item.put("LiftID", AttributeValue.fromN(String.valueOf(liftID)));
              item.put("ResortID", AttributeValue.fromN(String.valueOf(resortID)));
              item.put("Time", AttributeValue.fromN(String.valueOf(time)));

              PutItemRequest request = PutItemRequest.builder()
                  .tableName("LiftRides")
                  .item(item)
                  .build();

              ddb.putItem(request);

              System.out.println(Thread.currentThread().getName() + " Wrote to DynamoDB: " + message);
              channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
              System.err.println("❌ Failed to process message: " + e.getMessage());
              e.printStackTrace();
            }
          };

          channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    }

    // Keep main thread alive
    while (true) {
      Thread.sleep(60000);
    }
  }
}
