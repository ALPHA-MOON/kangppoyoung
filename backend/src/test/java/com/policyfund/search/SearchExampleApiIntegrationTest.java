package com.policyfund.search;

import com.policyfund.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class SearchExampleApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    private void add(String text) throws Exception {
        mvc.perform(post("/api/v1/search/examples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void add_list_delete_and_max5() throws Exception {
        for (int i = 1; i <= 5; i++) add("예시" + i);

        mvc.perform(get("/api/v1/search/examples"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(5));

        mvc.perform(post("/api/v1/search/examples")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"예시6\"}"))
           .andExpect(status().isConflict());

        String listJson = mvc.perform(get("/api/v1/search/examples"))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(listJson, "$[0].id").toString();

        mvc.perform(delete("/api/v1/search/examples/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/search/examples"))
           .andExpect(jsonPath("$.length()").value(4));
        add("새예시");
    }
}
