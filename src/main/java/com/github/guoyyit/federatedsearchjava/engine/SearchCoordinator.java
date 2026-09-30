package com.github.guoyyit.federatedsearchjava.engine;

import com.github.guoyyit.federatedsearchjava.config.SearchProperties;
import com.github.guoyyit.federatedsearchjava.config.WeightRegistry;
import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.domain.SearchRequest;
import com.github.guoyyit.federatedsearchjava.domain.SearchResponse;
import com.github.guoyyit.federatedsearchjava.domain.SourceReport;
import com.github.guoyyit.federatedsearchjava.domain.SourceState;
import com.github.guoyyit.federatedsearchjava.source.IndexSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

/**
 * Core orchestration: fans one query out to all enabled sources concurrently
 * under a global concurrency bound, enforces per-source timeouts, tolerates
 * failures with explainable degradation, normalizes weights, merges,
 * highlights, snapshots and pages.
 */
public class SearchCoordinator {

    private final List<IndexSource> sources;
    private final SearchProperties properties;
    private final WeightRegistry weights;
    private final ScoreNormalizer normalizer;
    private final Deduplicator deduplicator;
    private final Highlighter highlighter;
    private final SnapshotStore snapshotStore;
    private final ExecutorService executor;

    public SearchCoordinator(
            List<IndexSource> sources,
            SearchProperties properties,
            WeightRegistry weights,
            ScoreNormalizer normalizer,
            Deduplicator deduplicator,
            Highlighter highlighter,
            SnapshotStore snapshotStore) {
        this.sources = List.copyOf(sources);
        this.properties = properties;
        this.weights = weights;
        this.normalizer = normalizer;
        this.deduplicator = deduplicator;
        this.highlighter = highlighter;
        this.snapshotStore = snapshotStore;
        int poolSize = properties.maxConcurrentRequests() == null
                || properties.maxConcurrentRequests() <= 0 ? 8 : properties.maxConcurrentRequests();
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "federated-search-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        // A fixed pool is the global in-flight bound: at most poolSize
        // sub-queries from all concurrent requests run at the same time.
        this.executor = Executors.newFixedThreadPool(poolSize, factory);
    }

    /**
     * Runs a federated query. Never throws because a source was slow or
     * broken; every source outcome is captured in {@link SourceReport}s.
     */
    public SearchResponse search(SearchRequest request, CancellationToken token) {
        int page = Math.max(1, request.page() <= 0 ? 1 : request.page());
        int pageSize = request.pageSize() <= 0 ? 10 : Math.min(request.pageSize(), 200);

        if (page == 1 || request.snapshotId() == null || snapshotStore.get(request.snapshotId()) == null) {
            return runFirstPage(request, page, pageSize, token);
        }
        return pageFromSnapshot(request, page, pageSize);
    }

    private SearchResponse runFirstPage(SearchRequest request, int page, int pageSize, CancellationToken token) {
        List<SourceOutcome> outcomes = dispatch(request.query(), request.timeoutMs(), token);
        List<SearchHit> collected = new ArrayList<>();
        List<SourceReport> reports = new ArrayList<>();

        for (SourceOutcome outcome : outcomes) {
            reports.add(outcome.report);
            if (outcome.hits != null) {
                collected.addAll(outcome.hits);
            }
        }
        reports.sort(Comparator.comparing(SourceReport::source));

        List<SearchHit> normalized = normalizer.normalize(collected);
        normalized = applyWeights(normalized);
        DedupMode mode = resolveMode(request.dedupMode());
        List<SearchHit> merged = deduplicator.merge(normalized, mode);
        List<SearchHit> annotated = new ArrayList<>(merged.size());
        for (SearchHit hit : merged) {
            annotated.add(highlighter.annotate(hit, request.query()));
        }

        String snapshotId = snapshotStore.put(request.query(), mode, annotated);
        List<SearchHit> pageHits = slice(annotated, page, pageSize);
        boolean complete = reports.stream().allMatch(report -> report.state() == SourceState.COMPLETE);

        return new SearchResponse(
                request.query(),
                page,
                pageSize,
                annotated.size(),
                complete,
                !complete,
                reports,
                pageHits,
                snapshotId);
    }

    private SearchResponse pageFromSnapshot(SearchRequest request, int page, int pageSize) {
        Snapshot snapshot = snapshotStore.get(request.snapshotId());
        if (snapshot == null) {
            return runFirstPage(request, page, pageSize, new CancellationToken());
        }
        List<SearchHit> pageHits = slice(snapshot.hits(), page, pageSize);
        return new SearchResponse(
                snapshot.query(),
                page,
                pageSize,
                snapshot.hits().size(),
                true,
                false,
                List.of(),
                pageHits,
                snapshot.id());
    }

    private List<SearchHit> applyWeights(List<SearchHit> hits) {
        List<SearchHit> weighted = new ArrayList<>(hits.size());
        for (SearchHit hit : hits) {
            double finalScore = hit.normalizedScore() * weights.weightOf(hit.source());
            weighted.add(new SearchHit(
                    hit.documentId(),
                    hit.source(),
                    hit.title(),
                    hit.content(),
                    hit.url(),
                    hit.rawScore(),
                    hit.normalizedScore(),
                    finalScore,
                    hit.highlightedTitle(),
                    hit.snippet(),
                    hit.attributes(),
                    hit.indexedAt(),
                    hit.contentHash()));
        }
        return weighted;
    }

    private DedupMode resolveMode(DedupMode requested) {
        if (requested != null) {
            return requested;
        }
        try {
            return DedupMode.valueOf(properties.dedupBy() == null ? "DOCUMENT_ID" : properties.dedupBy());
        } catch (IllegalArgumentException e) {
            return DedupMode.DOCUMENT_ID;
        }
    }

    private static List<SearchHit> slice(List<SearchHit> hits, int page, int pageSize) {
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= hits.size()) {
            return List.of();
        }
        return List.copyOf(hits.subList(fromIndex, Math.min(hits.size(), fromIndex + pageSize)));
    }

    /**
     * Dispatches every enabled source on the bounded pool and waits with an
     * independent timeout per source. Completion order never affects the
     * result: outcomes are iterated in the configured source order.
     */
    private List<SourceOutcome> dispatch(String query, Long requestTimeout, CancellationToken token) {
        List<IndexSource> enabled = sources.stream().filter(IndexSource::enabled).toList();
        List<SourceTask> tasks = new ArrayList<>(enabled.size());

        for (IndexSource source : enabled) {
            Callable<List<SearchHit>> callable = () -> source.search(query, token);
            Future<List<SearchHit>> future;
            try {
                future = executor.submit(callable);
            } catch (RuntimeException e) {
                future = null;
            }
            long timeout = resolveTimeout(source.name(), requestTimeout);
            tasks.add(new SourceTask(source.name(), future, timeout));
        }

        long deadline = tasks.stream()
                .mapToLong(task -> task.timeoutMillis)
                .max().orElse(0L);
        long waitStart = System.nanoTime();

        List<SourceOutcome> outcomes = new ArrayList<>(tasks.size());
        boolean cancelled = false;
        while (outcomes.size() < tasks.size()) {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStart);
            if (isCancelled(token)) {
                cancelled = true;
                break;
            }
            if (elapsed >= deadline) {
                break;
            }
            boolean progressed = false;
            for (SourceTask task : tasks) {
                if (task.future == null || isRecorded(task, outcomes)) {
                    continue;
                }
                if (elapsed >= task.timeoutMillis) {
                    // Overdue: do NOT block on future.get(timeout), that
                    // would add another full timeout window to the query.
                    task.future.cancel(true);
                    outcomes.add(new SourceOutcome(
                            new SourceReport(task.source, SourceState.TIMEOUT,
                                    "source did not respond within " + task.timeoutMillis + " ms",
                                    elapsed, 0),
                            null));
                    progressed = true;
                } else if (task.future.isDone()) {
                    outcomes.add(collectDone(task, token, waitStart));
                    progressed = true;
                }
            }
            if (!progressed) {
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancelled = true;
                    break;
                }
            }
        }
        for (SourceTask task : tasks) {
            if (task.future == null) {
                outcomes.add(new SourceOutcome(
                        new SourceReport(task.source, SourceState.FAILED,
                                "executor rejected sub-query", 0L, 0),
                        null));
            } else if (!isRecorded(task, outcomes)) {
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStart);
                if (!task.future.isDone() && (cancelled || elapsed >= task.timeoutMillis)) {
                    task.future.cancel(true);
                    SourceState state = cancelled ? SourceState.CANCELLED : SourceState.TIMEOUT;
                    String reason = cancelled
                            ? "query cancelled by caller"
                            : "source did not respond within " + task.timeoutMillis + " ms";
                    outcomes.add(new SourceOutcome(
                            new SourceReport(task.source, state, reason, elapsed, 0), null));
                } else {
                    outcomes.add(collectDone(task, token, waitStart));
                }
            }
        }
        return outcomes;
    }

    private static boolean isRecorded(SourceTask task, List<SourceOutcome> outcomes) {
        for (SourceOutcome outcome : outcomes) {
            if (outcome.report.source().equals(task.source)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cancellation can arrive either cooperatively via the token (programmatic
     * callers / tests) or via worker-thread interruption (servlet-async
     * client disconnect). Bridge interruption into the token so already
     * running and queued sub-queries converge on the same cancel signal.
     */
    private static boolean isCancelled(CancellationToken token) {
        if (token.isCancelled()) {
            return true;
        }
        if (Thread.currentThread().isInterrupted()) {
            token.cancel();
            return true;
        }
        return false;
    }

    /**
     * Collects an already-finished future without waiting (callers only invoke
     * this once {@link java.util.concurrent.Future#isDone()} is true or the
     * overall deadline has elapsed and the future completed in the meantime).
     */
    private SourceOutcome collectDone(SourceTask task, CancellationToken token, long waitStartNanos) {
        try {
            List<SearchHit> hits = task.future.get();
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStartNanos);
            return new SourceOutcome(
                    new SourceReport(task.source, SourceState.COMPLETE, "ok", elapsed, hits.size()),
                    hits);
        } catch (Exception e) {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStartNanos);
            task.future.cancel(true);
            if (token.isCancelled() || isCancellation(e)) {
                return new SourceOutcome(
                        new SourceReport(task.source, SourceState.CANCELLED,
                                "query cancelled by caller", elapsed, 0),
                        null);
            }
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return new SourceOutcome(
                    new SourceReport(task.source, SourceState.FAILED,
                            cause.getClass().getSimpleName() + ": " + safeMessage(cause), elapsed, 0),
                    null);
        }
    }

    private static boolean isCancellation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CancelledException
                    || current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long resolveTimeout(String sourceName, Long requestTimeout) {
        Long configured = properties.sources().stream()
                .filter(props -> sourceName.equals(props.name()))
                .map(SearchProperties.SourceProps::timeoutMs)
                .findFirst()
                .orElse(null);
        if (requestTimeout != null) {
            return requestTimeout;
        }
        if (configured != null) {
            return configured;
        }
        return properties.defaultTimeoutMs() == null ? 800L : properties.defaultTimeoutMs();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? "(no message)" : message;
    }

    /** Called on shutdown to release worker threads. */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private record SourceTask(String source, Future<List<SearchHit>> future, long timeoutMillis) {
    }

    private record SourceOutcome(SourceReport report, List<SearchHit> hits) {
    }
}
