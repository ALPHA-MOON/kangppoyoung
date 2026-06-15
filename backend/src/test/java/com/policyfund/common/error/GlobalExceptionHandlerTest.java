package com.policyfund.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class,
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
            org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
        })
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
@TestPropertySource(properties = "spring.web.resources.add-mappings=false")
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;

    @RestController
    static class TestController {
        @GetMapping("/_test/notfound")
        public void notFound() { throw new ResourceNotFoundException("NOT_FOUND", "리소스가 없습니다"); }

        @GetMapping("/_test/boom")
        public void boom() { throw new IllegalStateException("내부 디테일 노출 금지"); }

        @PostMapping("/_test/validate")
        public void validate(@RequestBody @Valid Payload payload) { }

        record Payload(@NotBlank String name) {}
    }

    @Test
    void resourceNotFound_returns404WithCode() throws Exception {
        mvc.perform(get("/_test/notfound"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("NOT_FOUND"))
           .andExpect(jsonPath("$.message").value("리소스가 없습니다"));
    }

    @Test
    void validationError_returns400() throws Exception {
        mvc.perform(post("/_test/validate")
                .contentType("application/json")
                .content("{\"name\":\"\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void uncaught_returns500WithFixedMessage() throws Exception {
        mvc.perform(get("/_test/boom"))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
           .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다"));
    }
}
