package cs6650.hw1.client;

import okhttp3.*;
import com.google.gson.Gson;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates a 2-phase load test:
 *   Phase 1: 32 threads, each 1000 requests => 32,000 total
 *   Phase 2: For example 64 threads, each 2625 requests => 168,000 total
 *   Overall => 200,000 requests
 *
 * Adjust numbers as needed to exactly match your assignment requirements.
 */
public class LoadTester {
  // Phase 1 constants
  private static final int PHASE1_THREAD_COUNT = 32;
  private static final int PHASE1_REQUESTS_PER_THREAD = 1000;
  private static final int PHASE1_TOTAL_REQUESTS = PHASE1_THREAD_COUNT * PHASE1_REQUESTS_PER_THREAD; // 32k

  // Phase 2 constants
  private static final int PHASE2_THREAD_COUNT = 64;
  private static final int PHASE2_REQUESTS_PER_THREAD = 2625;
  private static final int PHASE2_TOTAL_REQUESTS = PHASE2_THREAD_COUNT * PHASE2_REQUESTS_PER_THREAD; // 168k

  // Overall sum => 200k
  private static final int TOTAL_REQUESTS = PHASE1_TOTAL_REQUESTS + PHASE2_TOTAL_REQUESTS;

  // Base URL
  private static final String BASE_URL = "http://44.232.3.127:8080/SkiLiftServer-1.0-SNAPSHOT";
  private static final ApiClient apiClient = ApiClient.getInstance();

  // Atomic counters
  private static final AtomicInteger successfulRequests = new AtomicInteger(0);
  private static final AtomicInteger failedRequests = new AtomicInteger(0);

  // For collecting latencies
  private static final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
  // For storing all request stats
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
    System.out.println("Total requests across all phases: " + TOTAL_REQUESTS);

    long startTime = System.nanoTime();

    // ---------------------
    // Phase 1
    // ---------------------
    System.out.println("=== Phase 1 ===");
    BlockingQueue<SkierEvent> eventQueuePhase1 = generateSkierEvents(PHASE1_TOTAL_REQUESTS);
    runPhase(PHASE1_THREAD_COUNT, PHASE1_REQUESTS_PER_THREAD, eventQueuePhase1, 1);

    // ---------------------
    // Phase 2
    // ---------------------
    System.out.println("=== Phase 2 ===");
    BlockingQueue<SkierEvent> eventQueuePhase2 = generateSkierEvents(PHASE2_TOTAL_REQUESTS);
    runPhase(PHASE2_THREAD_COUNT, PHASE2_REQUESTS_PER_THREAD, eventQueuePhase2, 2);

    // All phases done
    apiClient.shutdown();

    // Calculate total time
    printStatistics(startTime);
    writeResultsToCSV();
  }

  /**
   * runPhase: Creates a fixed thread pool, each thread sends 'requestsPerThread' requests,
   *           reading from eventQueue, then terminates.
   */
  private static void runPhase(int threadCount, int requestsPerThread,
      BlockingQueue<SkierEvent> eventQueue, int phaseId) throws InterruptedException {
    System.out.printf("Starting Phase %d with %d threads, each %d requests => %d total.\n",
        phaseId, threadCount, requestsPerThread, threadCount * requestsPerThread);

    CountDownLatch phaseLatch = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    for (int i = 0; i < threadCount; i++) {
      executor.execute(() -> {
        for (int r = 0; r < requestsPerThread; r++) {
          SkierEvent event = null;
          try {
            event = eventQueue.poll(5, TimeUnit.SECONDS);
            if (event == null) {
              break;
            }
            sendOneRequest(event);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
        }
        phaseLatch.countDown();
      });
    }

    phaseLatch.await();
    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);

    System.out.printf("Phase %d done.\n", phaseId);
  }

  /**
   * sendOneRequest: sends exactly 1 POST request, with up to 5 retries on failure
   */
  private static void sendOneRequest(SkierEvent event) {
    long requestStartTime = System.currentTimeMillis();
    int responseCode = 0;
    boolean success = false;

    // example endpoint: /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
    String endpoint = String.format("skiers/%d/seasons/%d/days/%d/skiers/%d",
        event.getResortID(), event.getSeasonID(), event.getDayID(), event.getSkierID());

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
  }

  /**
   * generateSkierEvents: produce 'count' random SkierEvent objects
   * to simulate random skierID, liftID, time, etc.
   */
  private static BlockingQueue<SkierEvent> generateSkierEvents(int count) {
    BlockingQueue<SkierEvent> queue = new LinkedBlockingQueue<>(count);
    for (int i = 0; i < count; i++) {
      SkierEvent event = new SkierEvent();
      queue.add(event);
    }
    return queue;
  }

  private static void printStatistics(long startTime) {
    long endTime = System.nanoTime();
    double totalTimeSec = (endTime - startTime) / 1_000_000_000.0;
    double successRate = (double) successfulRequests.get() / TOTAL_REQUESTS * 100;
    double rps = successfulRequests.get() / totalTimeSec;

    // Sort latencies for median/p99
    synchronized (latencies) {
      Collections.sort(latencies);
      double meanResponseTime = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
      int p99Index = (int) Math.max(0, latencies.size() * 0.99 - 1);
      long p99 = (latencies.size() > 0) ? latencies.get(p99Index) : 0;
      long minResponseTime = (latencies.size() > 0) ? latencies.get(0) : 0;
      long maxResponseTime = (latencies.size() > 0) ? latencies.get(latencies.size() - 1) : 0;
      double medianResponseTime = (latencies.size() > 0) ? latencies.get(latencies.size() / 2) : 0;

      System.out.println("\n======= Load Test Statistics =======");
      System.out.printf("Total requests (across phases):   %,d\n", TOTAL_REQUESTS);
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

