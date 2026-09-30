package com.github.guoyyit.federatedsearchjava.federation;

import com.github.guoyyit.federatedsearchjava.model.SearchHit;
import com.github.guoyyit.federatedsearchjava.model.SearchRequest;
import com.github.guoyyit.federatedsearchjava.model.SearchResponse;
import com.github.guoyyit.federatedsearchjava.model.SourceDegradation;
import com.github.guoyyit.federatedsearchjava.source.SearchIndexSource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class FederatedSearchService {

    private final SourceDispatcher dispatcher;
    private final ResultMerger merger;
    private final SnapshotStore snapshotStore;
    private final List<SearchIndexSource> sources;
    private final Clock clock;

    @Autowired
    public FederatedSearchService(SourceDispatcher dispatcher,
                                  ResultMerger merger,
                                  SnapshotStore snapshotStore,
                                  List<SearchIndexSource> sources) {
        this(dispatcher, merger, snapshotStore, sources, Clock.systemUTC());
    }

    FederatedSearchService(SourceDispatcher dispatcher,
                           ResultMerger merger,
                           SnapshotStore snapshotStore,
                           List<SearchIndexSource> sources,
                           Clock clock) {
        this.dispatcher = dispatcher;
        this.merger = merger;
        this.snapshotStore = snapshotStore;
        this.sources = List.copyOf(sources);
        this.clock = clock;
    }

    public SearchTask submit(SearchRequest request) {
        if (request.snapshotId() != null && !request.snapshotId().isBlank()) {
            ResultSnapshot snapshot = snapshotStore.find(request.snapshotId())
                    .orElseThrow(() -> new SnapshotExpiredException(request.snapshotId()));
            return SearchTask.completed(pageOf(snapshot, request));
        }
        SourceInvocation invocation = dispatcher.invoke(sources, request.query());
        return SearchTask.ofInvocation(invocation, completed -> buildFirstResponse(request, completed));
    }

    public SearchResponse search(SearchRequest request) throws InterruptedException {
        return submit(request).await();
    }

    private SearchResponse buildFirstResponse(SearchRequest request, SourceInvocation invocation) {
        List<SearchHit> merged = merger.merge(invocation.hits(), request.query());
        List<SourceDegradation> degradations = List.copyOf(invocation.degradations());
        ResultSnapshot snapshot = new ResultSnapshot(
                UUID.randomUUID().toString(),
                request.query(),
                clock.instant(),
                merged,
                degradations,
                degradations.isEmpty()
        );
        snapshotStore.put(snapshot);
        return pageOf(snapshot, request);
    }

    private SearchResponse pageOf(ResultSnapshot snapshot, SearchRequest request) {
        int pageSize = Math.max(1, request.pageSize());
        int page = Math.max(0, request.page());
        int from = Math.min(page * pageSize, snapshot.hits().size());
        int to = Math.min(from + pageSize, snapshot.hits().size());
        List<SearchHit> hits = List.copyOf(snapshot.hits().subList(from, to));
        return new SearchResponse(
                snapshot.query(),
                snapshot.id(),
                page,
                pageSize,
                snapshot.hits().size(),
                hits,
                snapshot.complete(),
                snapshot.degradations()
        );
    }
}
