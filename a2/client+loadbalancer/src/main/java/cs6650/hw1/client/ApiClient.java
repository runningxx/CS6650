package cs6650.hw1.client;

import okhttp3.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.concurrent.*;

public class ApiClient {
  private static final String BASE_URL = "http://ski-lift-balancer-1977574903.us-west-2.elb.amazonaws.com:8080/SkiLiftServer-1.0-SNAPSHOT";
  private static ApiClient instance;
  private final OkHttpClient httpClient;
  private final Gson gson;

  private ApiClient() {
    this.httpClient = new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build();
    this.gson = new Gson();

    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  public static synchronized ApiClient getInstance() {
    if (instance == null) {
      instance = new ApiClient();
    }
    return instance;
  }

  public String sendGetRequest(String endpoint) throws IOException {
    HttpUrl url = buildUrl(endpoint);

    Request request = new Request.Builder()
        .url(url)
        .get()
        .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Unexpected code " + response.code());
      }
      ResponseBody body = response.body();
      return body != null ? body.string() : "";
    }
  }

  public String sendPostRequest(String endpoint, Object data) throws IOException {
    String json = gson.toJson(data);
    RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
    HttpUrl url = buildUrl(endpoint);

    int maxRetries = 3;
    int attempt = 0;

    while (attempt < maxRetries) {
      attempt++;
      try {
        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
          if (response.isSuccessful()) {
            ResponseBody responseBody = response.body();
            return responseBody != null ? responseBody.string() : "";
          } else {
            System.err.println("[POST] Attempt " + attempt + " failed with response: " + response.code());
          }
        }
      } catch (IOException e) {
        System.err.println("[POST] Attempt " + attempt + " failed: " + e.getMessage());
      }

      if (attempt < maxRetries) {
        try {
          Thread.sleep(500);
        } catch (InterruptedException ignored) {}
      }
    }

    throw new IOException("POST request failed after " + maxRetries + " attempts");
  }

  private HttpUrl buildUrl(String endpoint) throws IOException {
    String formattedBaseUrl = BASE_URL.endsWith("/") ? BASE_URL.substring(0, BASE_URL.length() - 1) : BASE_URL;
    HttpUrl url = HttpUrl.parse(formattedBaseUrl + "/" + endpoint);
    if (url == null) {
      throw new IOException("Malformed URL: " + formattedBaseUrl + "/" + endpoint);
    }
    return url;
  }

  public void shutdown() {
    System.out.println("Initiating graceful shutdown...");

    httpClient.dispatcher().cancelAll();
    httpClient.dispatcher().executorService().shutdown();
    try {
      if (!httpClient.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS)) {
        System.err.println("Forcing OkHttp dispatcher shutdown.");
        httpClient.dispatcher().executorService().shutdownNow();
      }
    } catch (InterruptedException e) {
      httpClient.dispatcher().executorService().shutdownNow();
    }

    httpClient.connectionPool().evictAll();
    System.out.println("Shutdown completed.");
  }
}