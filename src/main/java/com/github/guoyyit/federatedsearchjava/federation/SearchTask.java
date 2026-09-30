package com.github.guoyyit.federatedsearchjava.federation;

import com.github.guoyyit.federatedsearchjava.model.SearchResponse;

import java.time.Duration;
import java.util.function.Function;

public class SearchTask {

    private final SourceInvocation invocation;
    private final SearchResponse readyResponse;
    private final Function<SourceInvocation, SearchResponse> responseBuilder;

    private SearchTask(SourceInvocation invocation,
                       SearchResponse readyResponse,
                       Function<SourceInvocation, SearchResponse> responseBuilder) {
        this.invocation = invocation;
        this.readyResponse = readyResponse;
        this.responseBuilder = responseBuilder;
    }

    public static SearchTask ofInvocation(SourceInvocation invocation,
                                          Function<SourceInvocation, SearchResponse> responseBuilder) {
        return new SearchTask(invocation, null, responseBuilder);
    }

    public static SearchTask completed(SearchResponse response) {
        return new SearchTask(null, response, null);
    }

    public SearchResponse await() throws InterruptedException {
        if (readyResponse != null) {
            return readyResponse;
        }
        invocation.await();
        return responseBuilder.apply(invocation);
    }

    public SearchResponse await(Duration timeout) throws InterruptedException {
        if (readyResponse != null) {
            return readyResponse;
        }
        if (!invocation.await(timeout)) {
            invocation.cancel();
        }
        return responseBuilder.apply(invocation);
    }

    public boolean cancel() {
        if (invocation == null) {
            return false;
        }
        return invocation.cancel();
    }
}
