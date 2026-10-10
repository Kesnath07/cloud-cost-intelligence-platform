package com.cloudcost.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudcost.support.PostgresIntegrationTest;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class WebSecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void pagesRequireSignIn() throws Exception {
        mvc.perform(get("/budgets")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void stateChangingFormsRequireACsrfToken() throws Exception {
        mvc.perform(post("/data-sources/demo").with(user("admin"))).andExpect(status().isForbidden());
        mvc.perform(post("/data-sources/demo").with(user("admin")).with(csrf())).andExpect(status().is3xxRedirection());
    }

    @Test
    void responsesCarryAStrictContentSecurityPolicy() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(header().string("Content-Security-Policy", Matchers.containsString("script-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
