package cs6650.hw1.client;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTester {
  private static final int NUM_THREADS_PHASE1 = 32;
  private static final int REQUESTS_PER_THREAD_PHASE1 = 1000;
  private static final int TOTAL_REQUESTS = 200000;
  private static final ApiClient apiClient = ApiClient.getInstance();
  private static final AtomicInteger successfulRequests = new AtomicInteger(0);
  private static final AtomicInteger failedRequests = new AtomicInteger(0);
  private static final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
  private static final List<Integer> throughputOverTime = Collections.synchronizedList(new ArrayList<>());

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

  private static final List<RequestStat> requestStats = Collections.synchronizedList(new ArrayList<>());

  public static void main(String[] args) throws InterruptedException {
    System.out.println("⌛️LoadTester is starting 200K concurrent requests with " + NUM_THREADS_PHASE1 + " threads...");
    long startTime = System.nanoTime();
    ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS_PHASE1);

    CountDownLatch phase1Latch = new CountDownLatch(NUM_THREADS_PHASE1);
    for (int i = 0; i < NUM_THREADS_PHASE1; i++) {
      executor.execute(() -> {
        sendRequests(REQUESTS_PER_THREAD_PHASE1);
        phase1Latch.countDown();
      });
    }
    phase1Latch.await();

    int remainingRequests = TOTAL_REQUESTS - (NUM_THREADS_PHASE1 * REQUESTS_PER_THREAD_PHASE1);
    int numThreadsPhase2 = 16;
    int requestsPerThreadPhase2 = remainingRequests / numThreadsPhase2;

    CountDownLatch phase2Latch = new CountDownLatch(numThreadsPhase2);
    for (int i = 0; i < numThreadsPhase2; i++) {
      executor.execute(() -> {
        sendRequests(requestsPerThreadPhase2);
        phase2Latch.countDown();
      });
    }
    phase2Latch.await();

    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);
    apiClient.shutdown();

    printStatistics(startTime);
    writeResultsToCSV();
  }

  private static void sendRequests(int numRequests) {
    long threadStartTime = System.currentTimeMillis();
    int requestsSent = 0;

    for (int i = 0; i < numRequests; i++) {
      SkierEvent event = new SkierEvent();
      long requestStartTime = System.currentTimeMillis();

      int responseCode = 0;
      boolean success = false;
      int retries = 0;

      while (retries < 5 && !success) {
        try {
          apiClient.sendPostRequest(
              "skiers/" + event.getSkierID() + "/seasons/" + event.getSeasonID() + "/day/" + event.getDayID() + "/skier/" + event.getLiftID(),
              event);
          responseCode = 201;
          successfulRequests.incrementAndGet();
          success = true;
        } catch (IOException e) {
          retries++;
          responseCode = 500;
          if (retries == 5) {
            failedRequests.incrementAndGet();
          }
        }
      }

      long requestEndTime = System.currentTimeMillis();
      long latency = requestEndTime - requestStartTime;
      latencies.add(latency);
      requestStats.add(new RequestStat(requestStartTime, "POST", latency, responseCode));
      requestsSent++;

      if ((requestEndTime - threadStartTime) >= 1000) {
        throughputOverTime.add(requestsSent);
        threadStartTime = requestEndTime;
        requestsSent = 0;
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
      long p99 = latencies.get(p99Index);
      long minResponseTime = latencies.get(0);
      long maxResponseTime = latencies.get(latencies.size() - 1);
      double medianResponseTime = latencies.get(latencies.size() / 2);

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
      System.out.println("🏆Load test results saved to " + fileName);
    } catch (IOException e) {
      System.err.println("☹️Error writing CSV file: " + e.getMessage());
    }
  }
}