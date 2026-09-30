package com.github.guoyyit.federatedsearchjava.federation;

import com.github.guoyyit.federatedsearchjava.model.DegradationReason;
import com.github.guoyyit.federatedsearchjava.model.SourceDegradation;
import com.github.guoyyit.federatedsearchjava.model.SourceHit;

import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SourceInvocation {

    private final List<SourceTask> tasks;
    private final CountDownLatch remaining;
    private volatile boolean finished;
    private volatile boolean cancelled;

    public SourceInvocation(List<SourceTask> tasks) {
        this.tasks = List.copyOf(tasks);
        this.remaining = new CountDownLatch(tasks.size());
    }

    public Map<String, List<SourceHit>> await() throws InterruptedException {
        remaining.await();
        finish();
        return collectHits();
    }

    public boolean await(Duration timeout) throws InterruptedException {
        boolean completed = remaining.await(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        if (completed) {
            finish();
        }
        return completed;
    }

    public boolean cancel() {
        if (finished) {
            return false;
        }
        cancelled = true;
        for (SourceTask task : tasks) {
            task.abort(DegradationReason.CANCELLED, "query cancelled by caller");
        }
        finish();
        return true;
    }

    public boolean isComplete() {
        return finished;
    }

    public List<SourceDegradation> degradations() {
        return tasks.stream()
                .map(SourceTask::degradation)
                .filter(deg -> deg != null)
                .toList();
    }

    public Map<String, List<SourceHit>> hits() {
        return collectHits();
    }

    void onTaskDone(SourceTask task) {
        remaining.countDown();
        if (remaining.getCount() == 0) {
            finish();
        }
    }

    private void finish() {
        finished = true;
    }

    boolean isCancelled() {
        return cancelled;
    }

    private Map<String, List<SourceHit>> collectHits() {
        ConcurrentHashMap<String, List<SourceHit>> hits = new ConcurrentHashMap<>();
        for (SourceTask task : tasks) {
            List<SourceHit> result = task.result();
            if (result != null) {
                hits.put(task.sourceId(), result);
            }
        }
        return Map.copyOf(hits);
    }

    static final class SourceTask {

        private final String sourceId;
        private final FutureTask<List<SourceHit>> future;
        private final ScheduledFuture<?> timeoutGuard;
        private volatile List<SourceHit> result;
        private volatile SourceDegradation degradation;

        SourceTask(String sourceId,
                   java.util.concurrent.Callable<List<SourceHit>> callable,
                   long timeoutMillis,
                   ScheduledExecutorService scheduler,
                   Runnable doneHandler) {
            this.sourceId = sourceId;
            this.future = new FutureTask<>(callable) {
                @Override
                protected void done() {
                    settle();
                    doneHandler.run();
                }
            };
            this.timeoutGuard = scheduler.schedule(
                    () -> abort(DegradationReason.TIMEOUT,
                            "source exceeded timeout of " + timeoutMillis + "ms"),
                    timeoutMillis, TimeUnit.MILLISECONDS);
        }

        FutureTask<List<SourceHit>> future() {
            return future;
        }

        String sourceId() {
            return sourceId;
        }

        List<SourceHit> result() {
            return result;
        }

        SourceDegradation degradation() {
            return degradation;
        }

        synchronized void abort(DegradationReason reason, String detail) {
            if (future.isDone() || degradation != null) {
                return;
            }
            degradation = new SourceDegradation(sourceId, reason, detail);
            future.cancel(true);
        }

        private void settle() {
            timeoutGuard.cancel(false);
            if (future.isCancelled()) {
                if (degradation == null) {
                    degradation = new SourceDegradation(sourceId,
                            DegradationReason.CANCELLED, "sub-query interrupted");
                }
                return;
            }
            try {
                List<SourceHit> hits = future.get();
                synchronized (this) {
                    if (degradation == null) {
                        result = List.copyOf(hits);
                    }
                }
            } catch (CancellationException e) {
                recordCancelled();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                recordCancelled();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof InterruptedException) {
                    recordCancelled();
                } else {
                    String detail = cause.getMessage() == null
                            ? cause.getClass().getSimpleName() : cause.getMessage();
                    degradation = new SourceDegradation(sourceId, DegradationReason.FAILED, detail);
                }
            }
        }

        private void recordCancelled() {
            if (degradation == null) {
                degradation = new SourceDegradation(sourceId,
                        DegradationReason.CANCELLED, "sub-query interrupted");
            }
        }
    }
}
