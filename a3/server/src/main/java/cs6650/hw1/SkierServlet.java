package cs6650.hw1;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;

@WebServlet(name = "SkierServlet", urlPatterns = {"/skiers/*"})
public class SkierServlet extends HttpServlet {

  private static final String QUEUE_NAME = "ski_lift_rides";

  private Connection connection;
  private static final int POOL_SIZE = 10;
  private BlockingQueue<Channel> channelPool;

  @Override
  public void init() throws ServletException {
    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("44.232.3.127");
      factory.setPort(5672);
      factory.setUsername("admin");
      factory.setPassword("admin123");

      connection = factory.newConnection();

      channelPool = new ArrayBlockingQueue<>(POOL_SIZE);
      for (int i = 0; i < POOL_SIZE; i++) {
        Channel ch = connection.createChannel();
        ch.queueDeclare(QUEUE_NAME, true, false, false, null);
        channelPool.offer(ch);
      }
      System.out.println("Initialized Channel Pool with size: " + POOL_SIZE);

    } catch (Exception e) {
      throw new ServletException("Failed to connect/init RabbitMQ channel pool", e);
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.setContentType("text/plain");
    resp.getWriter().write("It works!");
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.setContentType("application/json");

    // 1. URL 解析
    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.isEmpty()) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL format.");
      return;
    }

    String[] pathParts = pathInfo.split("/");
    if (pathParts.length != 8) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL structure.");
      return;
    }

    try {
      int resortID = Integer.parseInt(pathParts[1]);
      // pathParts[2] == "seasons"
      int seasonID = Integer.parseInt(pathParts[3]);
      // pathParts[4] == "days"
      int dayID = Integer.parseInt(pathParts[5]);
      // pathParts[6] == "skiers"
      int urlSkierID = Integer.parseInt(pathParts[7]);

      BufferedReader reader = req.getReader();
      JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

      if (!json.has("skierID") || !json.has("liftID") || !json.has("time")) {
        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing required fields.");
        return;
      }

      int jsonSkierID = json.get("skierID").getAsInt();
      if (urlSkierID != jsonSkierID) {
        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Skier ID mismatch between URL and payload.");
        return;
      }

      int liftID = json.get("liftID").getAsInt();
      int time = json.get("time").getAsInt();

      JsonObject message = new JsonObject();
      message.addProperty("resortID", resortID);
      message.addProperty("seasonID", seasonID);
      message.addProperty("dayID", dayID);
      message.addProperty("skierID", jsonSkierID);
      message.addProperty("liftID", liftID);
      message.addProperty("time", time);

      Channel ch = channelPool.take();
      try {
        ch.basicPublish("", QUEUE_NAME, null, message.toString().getBytes());
      } finally {
        channelPool.offer(ch);
      }

      resp.setStatus(HttpServletResponse.SC_CREATED);
      resp.getWriter().write("{\"message\": \"Lift ride recorded successfully\"}");

    } catch (NumberFormatException e) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid numerical value in URL");
    } catch (Exception e) {
      sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: " + e.getMessage());
    }
  }

  private void sendError(HttpServletResponse resp, int statusCode, String message) throws IOException {
    resp.setStatus(statusCode);
    JsonObject errorResponse = new JsonObject();
    errorResponse.addProperty("error", message);
    resp.getWriter().write(errorResponse.toString());
  }

  @Override
  public void destroy() {
    try {
      if (channelPool != null) {
        for (Channel ch : channelPool) {
          if (ch != null && ch.isOpen()) {
            ch.close();
          }
        }
      }
      if (connection != null && connection.isOpen()) {
        connection.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
