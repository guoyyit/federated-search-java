package com.github.guoyyit.federatedsearchjava.federation;

import com.github.guoyyit.federatedsearchjava.config.FederatedSearchProperties;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;

@Component
public class SnapshotStore {

    private final FederatedSearchProperties properties;
    private final Clock clock;
    private final LinkedHashMap<String, ResultSnapshot> snapshots = new LinkedHashMap<>();

    @Autowired
    public SnapshotStore(FederatedSearchProperties properties) {
        this(properties, Clock.systemUTC());
    }

    SnapshotStore(FederatedSearchProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void put(ResultSnapshot snapshot) {
        evictExpired();
        snapshots.put(snapshot.id(), snapshot);
        int max = Math.max(1, properties.getSnapshotMax());
        while (snapshots.size() > max) {
            Iterator<String> iterator = snapshots.keySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
    }

    public synchronized Optional<ResultSnapshot> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        ResultSnapshot snapshot = snapshots.get(id);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (isExpired(snapshot)) {
            snapshots.remove(id);
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }

    private void evictExpired() {
        Instant now = clock.instant();
        snapshots.entrySet().removeIf(entry -> {
            ResultSnapshot snapshot = entry.getValue();
            return snapshot.createdAt().plus(properties.getSnapshotTtl()).isBefore(now);
        });
    }

    private boolean isExpired(ResultSnapshot snapshot) {
        return snapshot.createdAt().plus(properties.getSnapshotTtl()).isBefore(clock.instant());
    }
}
