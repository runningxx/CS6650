package cs6650.hw1.client;

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

  public static void main(String[] args) throws InterruptedException {
    System.out.println("⌛ LoadTester is starting 200K concurrent requests with " + NUM_THREADS_PHASE1 + " threads....");
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
  }

  private static void sendRequests(int numRequests) {
    for (int i = 0; i < numRequests; i++) {
      SkierEvent event = new SkierEvent();
      int retries = 0;
      boolean success = false;

      while (retries < 5 && !success) {
        try {
          apiClient.sendPostRequest(
              "skiers/" + event.getSkierID() + "/seasons/" + event.getSeasonID() + "/day/" + event.getDayID() + "/skier/" + event.getLiftID(),
              event);
          successfulRequests.incrementAndGet();
          success = true;
        } catch (IOException e) {
          retries++;
          if (retries == 5) {
            failedRequests.incrementAndGet();
          }
        }
      }
    }
  }

  private static void printStatistics(long startTime) {
    long endTime = System.nanoTime();
    double totalTimeSec = (endTime - startTime) / 1_000_000_000.0;
    double rps = successfulRequests.get() / totalTimeSec;

    System.out.println("\n======= Load Test Statistics =======");
    System.out.printf("Successful requests:  %,d\n", successfulRequests.get());
    System.out.printf("Failed requests:      %,d\n", failedRequests.get());
    System.out.printf("Total time:          %.2f seconds\n", totalTimeSec);
    System.out.printf("Throughput:          %.2f RPS\n", rps);
    System.out.println("=====================================");
  }
}
