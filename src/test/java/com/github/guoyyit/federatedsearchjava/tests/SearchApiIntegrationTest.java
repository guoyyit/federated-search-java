package com.github.guoyyit.federatedsearchjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

/**
 * End-to-end checks against the real Spring beans and the three local
 * in-memory sources.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void queryMergesAllLocalSourcesWithCompleteReports() throws Exception {
        mockMvc.perform(search("federated search", 1, 10, "DOCUMENT_ID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.sourceReports.length()").value(3))
                .andExpect(jsonPath("$.sourceReports[0].state").value("COMPLETE"))
                .andExpect(jsonPath("$.hits[0].finalScore").exists())
                .andExpect(jsonPath("$.hits[0].snippet").isNotEmpty())
                .andExpect(jsonPath("$.snapshotId").isNotEmpty());
    }

    @Test
    void sharedArticleAndWikiDocumentIsDeduplicatedById() throws Exception {
        mockMvc.perform(search("federated search fans", 1, 50, "DOCUMENT_ID"))
                .andExpect(status().isOk())
                // articles + wiki both carry doc-federated-search; exactly one survives
                .andExpect(jsonPath("$.hits[?(@.documentId == 'doc-federated-search')]", hasSize(1)));
    }

    @Test
    void weightsCanBeReadAndUpdatedAtRuntime() throws Exception {
        mockMvc.perform(get("/api/search/weights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles").value(1.0));

        mockMvc.perform(post("/api/search/weights/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "weight": 7.5 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles").value(7.5));

        mockMvc.perform(get("/api/search/weights"))
                .andExpect(jsonPath("$.articles").value(7.5));

        // restore
        mockMvc.perform(post("/api/search/weights/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "weight": 1.0 }
                        """))
                .andExpect(status().isOk());
    }

    /**
     * Spring MVC processes a {@code Callable} controller in two phases: the
     * first perform starts async processing; we wait for it and then issue
     * the async dispatch that carries the resolved JSON body.
     */
    private RequestBuilder search(String query, int page, int pageSize, String dedupMode) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                        {
                          "query": "%s",
                          "page": %d,
                          "pageSize": %d,
                          "dedupMode": "%s",
                          "timeoutMs": 2000
                        }
                        """, query, page, pageSize, dedupMode));
        MvcResult started = mockMvc.perform(request).andReturn();
        started.getAsyncResult(5_000L);
        return asyncDispatch(started);
    }
}
