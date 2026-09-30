package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.model.SourceHit;
import com.github.guoyyit.federatedsearchjava.source.SearchIndexSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class MutableSource implements SearchIndexSource {

    private final String id;
    private final AtomicReference<List<SourceHit>> docs = new AtomicReference<>(List.of());

    MutableSource(String id) {
        this.id = id;
    }

    void setDocs(List<SourceHit> hits) {
        docs.set(List.copyOf(hits));
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<SourceHit> search(String query) {
        return docs.get();
    }
}
