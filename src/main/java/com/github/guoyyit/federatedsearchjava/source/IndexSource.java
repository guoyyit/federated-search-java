package com.github.guoyyit.federatedsearchjava.source;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;

import java.util.List;

/**
 * A local, in-process search index source. Implementations must respond to
 * thread interruption and to {@link CancellationToken} promptly, so that a
 * cancelled/overdue sub-query leaves no background work behind.
 */
public interface IndexSource {

    String name();

    boolean enabled();

    List<SearchHit> search(String query, CancellationToken cancellationToken) throws Exception;
}
