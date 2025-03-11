package cs6650.hw1.client;

import okhttp3.*;
import com.google.gson.Gson;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTester {
  private static final int NUM_THREADS_PHASE1 = 64;
  private static final int REQUESTS_PER_THREAD_PHASE1 = 5000;
  private static final int TOTAL_REQUESTS = NUM_THREADS_PHASE1 * REQUESTS_PER_THREAD_PHASE1;

  private static final String BASE_URL = "http://44.232.3.127:8080/SkiLiftServer-1.0-SNAPSHOT";
  private static final ApiClient apiClient = ApiClient.getInstance();

  private static final AtomicInteger successfulRequests = new AtomicInteger(0);
  private static final AtomicInteger failedRequests = new AtomicInteger(0);
  private static final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
  private static final BlockingQueue<SkierEvent> eventQueue = new LinkedBlockingQueue<>();
  private static final List<RequestStat> requestStats = Collections.synchronizedList(new ArrayList<>());

  static class RequestStat {
    long startTime;
    String requestType;
    long latency;
    int responseCode;

    public RequestStat(long startTime, String requestType, long latency, int responseCode) {
      this.startTime = startTime;
      this.requestType = requestType;
      this.latency = latency;
      this.responseCode = responseCode;
    }
  }

  public static void main(String[] args) throws InterruptedException {
    System.out.println("⌛ LoadTester is starting " + TOTAL_REQUESTS + " concurrent requests with " + NUM_THREADS_PHASE1 + " threads...");
    long startTime = System.nanoTime();
    ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS_PHASE1);

    for (int i = 0; i < TOTAL_REQUESTS; i++) {
      eventQueue.add(new SkierEvent());
    }

    CountDownLatch phase1Latch = new CountDownLatch(NUM_THREADS_PHASE1);
    for (int i = 0; i < NUM_THREADS_PHASE1; i++) {
      executor.execute(() -> {
        sendRequests();
        phase1Latch.countDown();
      });
    }

    phase1Latch.await();
    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);
    apiClient.shutdown();

    printStatistics(startTime);
    writeResultsToCSV();
  }

  private static void sendRequests() {
    while (true) {
      try {
        SkierEvent event = eventQueue.poll(5, TimeUnit.SECONDS);
        if (event == null) {
          break;
        }
        long requestStartTime = System.currentTimeMillis();
        int responseCode = 0;
        boolean success = false;

        String endpoint = "skiers/" + event.getResortID() + "/seasons/" + event.getSeasonID()
            + "/day/" + event.getDayID() + "/skier/" + event.getSkierID();

        for (int retry = 0; retry < 5 && !success; retry++) {
          try {
            apiClient.sendPostRequest(endpoint, event);
            responseCode = 201;
            successfulRequests.incrementAndGet();
            success = true;
          } catch (IOException e) {
            responseCode = 500;
            try {
              Thread.sleep(10);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            }
            if (retry == 4) {
              failedRequests.incrementAndGet();
            }
          }
        }

        long requestEndTime = System.currentTimeMillis();
        long latency = requestEndTime - requestStartTime;
        latencies.add(latency);
        requestStats.add(new RequestStat(requestStartTime, "POST", latency, responseCode));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private static void printStatistics(long startTime) {
    long endTime = System.nanoTime();
    double totalTimeSec = (endTime - startTime) / 1_000_000_000.0;
    double successRate = (double) successfulRequests.get() / TOTAL_REQUESTS * 100;
    double rps = successfulRequests.get() / totalTimeSec;

    synchronized (latencies) {
      Collections.sort(latencies);
      double meanResponseTime = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
      int p99Index = (int) Math.max(0, latencies.size() * 0.99 - 1);
      long p99 = (latencies.size() > 0) ? latencies.get(p99Index) : 0;
      long minResponseTime = (latencies.size() > 0) ? latencies.get(0) : 0;
      long maxResponseTime = (latencies.size() > 0) ? latencies.get(latencies.size() - 1) : 0;
      double medianResponseTime = (latencies.size() > 0) ? latencies.get(latencies.size() / 2) : 0;

      System.out.println("\n======= Load Test Statistics =======");
      System.out.printf("Total requests:   %,d\n", TOTAL_REQUESTS);
      System.out.printf("Threads used:     %,d\n", NUM_THREADS_PHASE1);
      System.out.printf("Successful:       %,d (%.2f%%)\n", successfulRequests.get(), successRate);
      System.out.printf("Failed:           %,d\n", failedRequests.get());
      System.out.printf("Total time:       %.2f seconds\n", totalTimeSec);
      System.out.printf("Requests/sec:     %.2f RPS\n", rps);
      System.out.printf("Mean Response Time:   %.2f ms\n", meanResponseTime);
      System.out.printf("Median Response Time: %.2f ms\n", medianResponseTime);
      System.out.printf("P99 Response Time:    %d ms\n", p99);
      System.out.printf("Min Response Time:    %d ms\n", minResponseTime);
      System.out.printf("Max Response Time:    %d ms\n", maxResponseTime);
      System.out.println("=====================================");
    }
  }

  private static void writeResultsToCSV() {
    String fileName = "load_test_results.csv";
    try (FileWriter writer = new FileWriter(fileName)) {
      writer.append("Start Time,Request Type,Latency (ms),Response Code\n");
      synchronized (requestStats) {
        for (RequestStat stat : requestStats) {
          writer.append(String.format("%d,POST,%d,%d\n", stat.startTime, stat.latency, stat.responseCode));
        }
      }
      System.out.println("🏆 Load test results saved to " + fileName);
    } catch (IOException e) {
      System.err.println("❌ Error writing CSV file: " + e.getMessage());
    }
  }
}

