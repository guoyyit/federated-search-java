package com.github.guoyyit.federatedsearchjava.engine;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the fully-merged, sorted hit list of the first page request so later
 * pages page over an immutable snapshot: source-side changes cannot cause
 * cross-page duplicates or misses.
 */
public class SnapshotStore {

    private final long ttlMillis;
    private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    public SnapshotStore(long ttlSeconds) {
        this.ttlMillis = Math.max(1L, ttlSeconds) * 1000L;
    }

    public String put(String query, DedupMode mode, List<SearchHit> hits) {
        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        List<SearchHit> frozen = List.copyOf(hits);
        snapshots.put(id, new Snapshot(id, query, mode, frozen, now, now + ttlMillis));
        purgeExpired();
        return id;
    }

    public Snapshot get(String snapshotId) {
        if (snapshotId == null) {
            return null;
        }
        Snapshot snapshot = snapshots.get(snapshotId);
        if (snapshot == null) {
            return null;
        }
        if (snapshot.expiresAtMillis() < System.currentTimeMillis()) {
            snapshots.remove(snapshotId);
            return null;
        }
        return snapshot;
    }

    public int purgeExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Snapshot snapshot : snapshots.values()) {
            if (snapshot.expiresAtMillis() < now && snapshots.remove(snapshot.id(), snapshot)) {
                removed++;
            }
        }
        return removed;
    }
}
