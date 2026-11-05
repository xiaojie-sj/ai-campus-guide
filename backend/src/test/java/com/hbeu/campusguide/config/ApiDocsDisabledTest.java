package com.hbeu.campusguide.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "springdoc.api-docs.enabled=false")
@AutoConfigureMockMvc
class ApiDocsDisabledTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void blocksKnife4jAndOpenApiEndpointsWhenDisabled() throws Exception {
        mockMvc.perform(get("/doc.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
