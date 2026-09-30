package com.github.guoyyit.federatedsearchjava.web;

import com.github.guoyyit.federatedsearchjava.config.WeightRegistry;
import com.github.guoyyit.federatedsearchjava.domain.SearchRequest;
import com.github.guoyyit.federatedsearchjava.domain.SearchResponse;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;
import com.github.guoyyit.federatedsearchjava.engine.SearchCoordinator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * HTTP facade. The search endpoint returns a {@link Callable}, so Spring MVC
 * runs it as a servlet-async request: when the caller disconnects (or the
 * async request errors/times out), Spring interrupts the worker thread. The
 * coordinator treats that interruption as query cancellation and interrupts
 * every dispatched sub-query in turn, so no background source work outlives
 * the request.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchCoordinator coordinator;
    private final WeightRegistry weights;

    public SearchController(SearchCoordinator coordinator, WeightRegistry weights) {
        this.coordinator = coordinator;
        this.weights = weights;
    }

    @PostMapping
    public Callable<SearchResponse> search(@RequestBody SearchRequest request) {
        // Created on the request thread, consumed on the async worker thread.
        CancellationToken token = new CancellationToken();
        return () -> coordinator.search(request, token);
    }

    @GetMapping("/weights")
    public Map<String, Double> weights() {
        return weights.all();
    }

    @PostMapping("/weights/{source}")
    public ResponseEntity<Map<String, Object>> updateWeight(
            @PathVariable String source,
            @RequestBody Map<String, Object> body) {
        Object value = body.get("weight");
        if (!(value instanceof Number number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing numeric 'weight'"));
        }
        double weight = number.doubleValue();
        if (weight < 0.0d) {
            return ResponseEntity.badRequest().body(Map.of("error", "weight must be non-negative"));
        }
        weights.updateWeight(source, weight);
        return ResponseEntity.ok(Map.copyOf(weights.all()));
    }
}
