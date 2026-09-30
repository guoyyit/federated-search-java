package com.github.guoyyit.federatedsearchjava.web;

import com.github.guoyyit.federatedsearchjava.federation.FederatedSearchService;
import com.github.guoyyit.federatedsearchjava.model.SearchRequest;
import com.github.guoyyit.federatedsearchjava.model.SearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final FederatedSearchService searchService;

    public SearchController(FederatedSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(@RequestParam("q") String query,
                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                 @RequestParam(value = "size", defaultValue = "10") int size,
                                 @RequestParam(value = "snapshotId", required = false) String snapshotId)
            throws InterruptedException {
        return searchService.search(new SearchRequest(query, page, size, snapshotId));
    }
}
