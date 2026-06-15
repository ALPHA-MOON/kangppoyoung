package com.policyfund.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.DummyController.class)
@Import({SecurityConfig.class, SecurityConfigTest.DummyController.class})
class SecurityConfigTest {

    @Autowired MockMvc mvc;

    @RestController
    static class DummyController {
        @PostMapping("/api/v1/_dummy")
        public String dummy() { return "ok"; }
    }

    @Test
    void postIsAllowedWithoutAuthOrCsrf() throws Exception {
        // permitAll + CSRF disabled => 200 without auth/CSRF token.
        // (CSRF on => 403; auth required => 401/403)
        mvc.perform(post("/api/v1/_dummy"))
           .andExpect(status().isOk());
    }
}
