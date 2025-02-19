# Questions for David.

## Why are we asking this questions


Profiling a system before refactoring is crucial for several reasons:

1. Identify Performance Bottlenecks

    Profiling helps pinpoint areas of the system that are slow or consume excessive resources (CPU, memory, I/O). This ensures that refactoring efforts are focused on the parts of the code that will yield the most significant performance improvements.

2. Understand System Behavior

    Profiling provides insights into how the system behaves under different conditions, such as high load or specific user interactions. This understanding is essential for making informed decisions during refactoring.

3. Avoid Introducing New Issues

    Without profiling, refactoring might inadvertently introduce new performance issues or bugs. Profiling helps establish a baseline, making it easier to detect and address any regressions after refactoring.

4. Prioritize Refactoring Efforts

    Not all parts of the system may need refactoring. Profiling helps prioritize which components or modules require attention, ensuring that time and resources are spent effectively.

5. Measure the Impact of Refactoring

    Profiling before and after refactoring allows you to quantify the improvements (e.g., reduced execution time, lower memory usage). This helps validate that the refactoring efforts were successful and provides measurable results.

6. Detect Hidden Issues

    Profiling can reveal hidden problems, such as memory leaks, inefficient algorithms, or excessive database queries, that might not be apparent during code reviews or static analysis.

7. Ensure Scalability

    Profiling helps identify whether the system can scale to handle increased loads. Refactoring based on profiling data can improve scalability and prevent future performance degradation.

8. Support Data-Driven Decisions

    Profiling provides concrete data to guide refactoring decisions, reducing reliance on assumptions or guesswork. This leads to more effective and targeted improvements.

9. Improve Code Maintainability

    Profiling often highlights overly complex or poorly structured code that is difficult to maintain. Refactoring these areas can improve code readability and maintainability.

10. Reduce Technical Debt

    By addressing performance and structural issues identified through profiling, refactoring helps reduce technical debt, making the system easier to work with in the long term.


1. System Performance Metrics

These metrics help assess the overall performance of the ENA system:

    Response Time / Latency:  Measure the time taken to respond to user requests, such as searching for sequences or retrieving data to completion.

    Throughput: Track the number of requests or transactions the system can handle per unit of time.

2. Resource Utilization Metrics

These metrics help identify bottlenecks in hardware and software resources:

    CPU Usage: Monitor CPU utilization to identify computationally intensive operations.

    Memory Usage: Track memory consumption to detect memory leaks or inefficient memory management.

    Disk I/O: Measure read/write operations to assess storage performance and identify potential bottlenecks.

    Network Bandwidth: Monitor network traffic to ensure efficient data transfer between users and the archive.

    Database Load: Track the load on the database system, including connection pools, query queues, and indexing performance.

3. Data Access Patterns

    Popular Requests: Identify the most frequently executed queries to optimize their performance.

    User Behavior: Track how users navigate the system, including common search terms, filters and workflows. 

4. Storage and Data Management Metrics

    Data Growth Rate: Measure how quickly new data is being added to the archive, should be a single score eg, year over year growth.

    Storage Utilization: Track how much storage is used.

    Data Redundancy: Assess the level of data duplication with a single score, eg % of dup over total.

    Backup and Recovery Performance: In case of disaster recovery, how long does it take to recover the system.

5. Scalability and Load Handling

    Concurrent Users: Measure the number of simultaneous file downloads during peak times. Where is the bottleneck, I/O, cpu, memory... 

6. Error and Failure Metrics

    Error Rates: Monitor the frequency of failed queries, timeouts, or other errors.

    System Downtime: Measure the availability of the system and identify causes of downtime.

    User-Reported Issues: Stats on issues reported by users and recurrent problems.


7. Cost Efficiency Metrics

    Average Cost per Query: Measure the computational and storage costs associated with handling user queries.

    Total Infrastructure Costs: Track the cost of maintaining servers, storage, and network resources.

    Total Energy Consumption: Monitor the energy usage of data centers hosting the ENA.
