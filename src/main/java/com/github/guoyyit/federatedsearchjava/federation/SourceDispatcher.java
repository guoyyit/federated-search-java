package com.github.guoyyit.federatedsearchjava.federation;

import com.github.guoyyit.federatedsearchjava.config.FederatedSearchProperties;
import com.github.guoyyit.federatedsearchjava.source.SearchIndexSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SourceDispatcher {

    private final FederatedSearchProperties properties;
    private final ExecutorService workerPool;
    private final ScheduledExecutorService timeoutScheduler;

    public SourceDispatcher(FederatedSearchProperties properties) {
        this.properties = properties;
        int concurrency = Math.max(1, properties.getGlobalConcurrency());
        this.workerPool = Executors.newFixedThreadPool(concurrency, namedFactory("federated-source-"));
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(
                namedFactory("federated-timeout-"));
    }

    public SourceInvocation invoke(List<SearchIndexSource> sources, String query) {
        List<SourceInvocation.SourceTask> tasks = new ArrayList<>(sources.size());
        SourceInvocation[] holder = new SourceInvocation[1];
        for (SearchIndexSource source : sources) {
            long timeoutMillis = timeoutFor(source.getId()).toMillis();
            SourceInvocation.SourceTask task = new SourceInvocation.SourceTask(
                    source.getId(),
                    () -> source.search(query),
                    timeoutMillis,
                    timeoutScheduler,
                    () -> holder[0].onTaskDone(taskReference(tasks, source.getId()))
            );
            tasks.add(task);
        }
        SourceInvocation created = new SourceInvocation(tasks);
        holder[0] = created;
        for (SourceInvocation.SourceTask task : tasks) {
            workerPool.execute(task.future());
        }
        return created;
    }

    private SourceInvocation.SourceTask taskReference(
            List<SourceInvocation.SourceTask> tasks, String sourceId) {
        for (SourceInvocation.SourceTask task : tasks) {
            if (task.sourceId().equals(sourceId)) {
                return task;
            }
        }
        throw new IllegalStateException("task not found for source " + sourceId);
    }

    private Duration timeoutFor(String sourceId) {
        FederatedSearchProperties.SourceConfig config = properties.getSources().get(sourceId);
        if (config == null || config.getTimeout() == null || config.getTimeout().isZero()
                || config.getTimeout().isNegative()) {
            return Duration.ofSeconds(2);
        }
        return config.getTimeout();
    }

    @PreDestroy
    public void shutdown() {
        timeoutScheduler.shutdownNow();
        workerPool.shutdownNow();
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
