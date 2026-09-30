package com.github.guoyyit.federatedsearchjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchHttpIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void searchReturnsMergedResultsAndSnapshotPagingWorks() {
        ResponseEntity<Map> first = restTemplate.getForEntity(
                url("/api/search?q=keyboard&page=0&size=2"), Map.class);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        Map<String, Object> body = first.getBody();
        assertNotNull(body);
        assertEquals(Boolean.TRUE, body.get("complete"));
        assertTrue(((List<?>) body.get("degradations")).isEmpty());
        assertEquals(2, ((List<?>) body.get("hits")).size());
        Number total = (Number) body.get("totalHits");
        assertTrue(total.intValue() >= 2);
        String snapshotId = (String) body.get("snapshotId");
        assertNotNull(snapshotId);

        ResponseEntity<Map> second = restTemplate.getForEntity(
                url("/api/search?q=keyboard&page=1&size=2&snapshotId=" + snapshotId), Map.class);
        assertEquals(HttpStatus.OK, second.getStatusCode());
        List<?> firstHits = (List<?>) body.get("hits");
        List<?> nextHits = (List<?>) second.getBody().get("hits");
        Map<?, ?> firstOfPageOne = (Map<?, ?>) firstHits.get(0);
        Map<?, ?> firstOfPageTwo = (Map<?, ?>) nextHits.get(0);
        assertFalse(firstOfPageOne.get("docId").equals(firstOfPageTwo.get("docId")),
                "consecutive pages must not overlap");
        assertEquals(snapshotId, second.getBody().get("snapshotId"));
    }

    @Test
    void weightAdjustmentIsImmediatelyReflectedInSearch() {
        restTemplate.put(url("/api/sources/news/weight?weight=100.0"), null);
        ResponseEntity<Map> boosted = restTemplate.getForEntity(
                url("/api/search?q=keyboard&size=20"), Map.class);
        List<?> hits = (List<?>) boosted.getBody().get("hits");
        Map<?, ?> top = (Map<?, ?>) hits.get(0);
        assertEquals("news", ((List<?>) top.get("matchedSources")).get(0));

        restTemplate.put(url("/api/sources/news/weight?weight=1.2"), null);
    }

    @Test
    void expiredSnapshotReturns410() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/search?q=keyboard&snapshotId=does-not-exist"), Map.class);
        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertEquals("SNAPSHOT_EXPIRED", response.getBody().get("error"));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
