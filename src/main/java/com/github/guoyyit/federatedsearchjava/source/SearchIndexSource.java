package com.github.guoyyit.federatedsearchjava.source;

import com.github.guoyyit.federatedsearchjava.model.SourceHit;

import java.util.List;

public interface SearchIndexSource {

    String getId();

    List<SourceHit> search(String query) throws InterruptedException;
}
