package com.hbeu.campusguide.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesKnife4jAndGroupedDocuments() throws Exception {
        mockMvc.perform(get("/doc.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.paths['/api/public/bootstrap']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/points']").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));

        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/points']").exists())
                .andExpect(jsonPath("$.paths['/api/public/bootstrap']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/points'].get.security[0].bearerAuth").exists());
    }
}
