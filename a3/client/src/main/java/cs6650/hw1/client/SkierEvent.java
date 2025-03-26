package cs6650.hw1.client;

import java.util.Random;

public class SkierEvent {
  private static final Random RANDOM = new Random();

  private final int skierID;
  private final int resortID;
  private final int liftID;
  private final int seasonID;
  private final int dayID;
  private final int time;

  public SkierEvent() {
    this.skierID = RANDOM.nextInt(100000) + 1;
    this.resortID = RANDOM.nextInt(10) + 1;
    this.liftID = RANDOM.nextInt(40) + 1;
    this.seasonID = 2025;
    this.dayID = 1;
    this.time = RANDOM.nextInt(360) + 1;
  }

  public int getSkierID() { return skierID; }
  public int getResortID() { return resortID; }
  public int getLiftID() { return liftID; }
  public int getSeasonID() { return seasonID; }
  public int getDayID() { return dayID; }
  public int getTime() { return time; }
}
