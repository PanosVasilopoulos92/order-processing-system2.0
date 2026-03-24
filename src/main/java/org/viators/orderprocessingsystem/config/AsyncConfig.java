package org.viators.orderprocessingsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures asynchronous event processing for the CQRS event bridge.
 *
 * Why async? In CQRS, the command side publishes events after a successful
 * write operation. The query side (event listener) processes those events
 * to update the read model. If event processing is synchronous, a failure
 * in the read model update would roll back the write transaction — breaking
 * the independence of the two sides.
 *
 * With @EnableAsync, event listeners annotated with @Async run on a separate
 * thread pool. The command side completes its transaction independently,
 * and the query side processes events on its own timeline.
 *
 * Trade-off: This introduces eventual consistency. The read model may be
 * briefly out of date after write. For most use cases (product catalogs,
 * dashboards, search results), this is perfectly acceptable.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Custom thread pool for CQRS event processing.
     *
     * Why not use the default SimpleAsyncTaskExecutor? The default creates
     * a new thread for every async invocation — no pooling, no queue, no
     * backpressure. In production, a burst of events could spawn thousands
     * of threads and crash the JVM. A ThreadPoolTaskExecutor provides:
     * - Bounded thread count (core + max)
     * - A queue to buffer events during spikes
     * - Graceful shutdown with awaitTermination
     *
     * @return a configured thread pool executor for async event handling
     */
    @Bean(name = "cqrsEventExecutor")
    public Executor cqrsEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core threads: always alive, ready to process events immediately.
        // Start with 2 — enough for typical event throughput in a monolith.
        executor.setCorePoolSize(2);

        // Max threads: upper bound during event spikes.
        // Only created when the queue is full.
        executor.setMaxPoolSize(5);

        // Queue capacity: buffer events when all core threads are busy.
        // 100 is generous for in-process events. If you regularly exceed this,
        // it's a sign you should move to a message broker (Kafka, RabbitMQ).
        executor.setQueueCapacity(100);

        // Thread name prefix: makes thread dumps readable.
        // You'll see "cqrs-event-1", "cqrs-event-2" in logs and dumps.
        executor.setThreadNamePrefix("cqrs-event-");

        // Wait for queued events to finish during application shutdown.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

}
