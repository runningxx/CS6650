# CS6650 Assignment 1 - Client

## Overview
- Purpose: Client implementation that sends 200,000 POST requests using multi-threading for performance testing
- Components: Two client implementations (Part 1 and Part 2)

## Key Components

### Client Part 1
- Basic load testing implementation
- Sends 200K requests using 32 threads

### Client Part 2
- Extends Part 1 functionality
- Additional feature: Records and reports latency statistics
    - Mean response time
    - Median response time
    - P99 response time

## Technical Requirements
- Java 8 or above
- Maven
- Running server on AWS EC2
- Port 8080 must be open in EC2 security group

## Setup Instructions

1. Configure Server URL in `ApiClient.java`:
```java
private static final String BASE_URL = "http://<your-ec2-public-ip>:8080/SkiLiftServer-1.0-SNAPSHOT/";
```

2. Run Client Part 1:
```java
cd client-part1
mvn clean compile exec:java -Dexec.mainClass="cs6650.hw1.client.LoadTester"
```

3. Run Client Part 2:
```java
cd client-part2
mvn clean compile exec:java -Dexec.mainClass="cs6650.hw1.client.LoadTester"
```

## Key Features
- 32 threads for concurrent requests
- Request retry mechanism (up to 5 attempts)
- Comprehensive performance metrics
- 200,000 total POST requests

## Expected Output Example
```plaintext
Total requests: 200,000
Threads used: 32
Successful: 200,000 (100.00%)
Failed: 0
Total time: 197.20 seconds
Requests/sec: 1014.19 RPS
Mean Response Time: 17.11 ms
Median Response Time: 17.00 ms
P99 Response Time: 29 ms
```
