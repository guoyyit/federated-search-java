package com.github.guoyyit.federatedsearchjava.web;

import com.github.guoyyit.federatedsearchjava.federation.SourceWeightRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sources")
public class WeightAdminController {

    private final SourceWeightRegistry weightRegistry;

    public WeightAdminController(SourceWeightRegistry weightRegistry) {
        this.weightRegistry = weightRegistry;
    }

    @PutMapping("/{sourceId}/weight")
    public Map<String, Object> setWeight(@PathVariable String sourceId,
                                         @RequestParam("weight") double weight) {
        weightRegistry.setWeight(sourceId, weight);
        return Map.of("sourceId", sourceId, "weight", weightRegistry.getWeight(sourceId));
    }

    @GetMapping("/{sourceId}/weight")
    public Map<String, Object> getWeight(@PathVariable String sourceId) {
        return Map.of("sourceId", sourceId, "weight", weightRegistry.getWeight(sourceId));
    }
}
