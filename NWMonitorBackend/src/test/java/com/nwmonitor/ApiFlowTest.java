package com.nwmonitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nwmonitor.device.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:nwm;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "nwm.security.jwt-secret=test-secret-test-secret-test-secret-123456"})
@AutoConfigureMockMvc
class ApiFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DeviceService devices;

    private ResultActions send(MockHttpServletRequestBuilder b, String token, Object body) throws Exception {
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(b);
    }

    private JsonNode body(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }

    private String login(String user, String pw) throws Exception {
        return body(send(post("/api/v1/auth/login"), null, java.util.Map.of("username", user, "password", pw)).andExpect(status().isOk())).get("accessToken").asText();
    }

    @Test
    void rootAdminAndIngestFlow() throws Exception {
        // first-run setup creates the single root, and only once
        send(get("/api/v1/setup/status"), null, null).andExpect(jsonPath("$.setupRequired").value(true));
        var root = java.util.Map.of("username", "root", "displayName", "Root", "password", "correct-horse-battery");
        send(post("/api/v1/setup/root"), null, root).andExpect(status().isCreated());
        send(post("/api/v1/setup/root"), null, java.util.Map.of("username", "root2", "displayName", "R2", "password", "correct-horse-battery"))
                .andExpect(status().isConflict());

        // protected endpoints need a token
        send(get("/api/v1/devices"), null, null).andExpect(status().isUnauthorized());

        String rootToken = login("root", "correct-horse-battery");
        JsonNode admin = body(send(post("/api/v1/users"), rootToken,
                java.util.Map.of("username", "alice", "displayName", "Alice", "password", "alice-long-password")).andExpect(status().isCreated()));
        assertEquals("ADMIN", admin.get("role").asText());

        // admins have the same access except user management
        String adminToken = login("alice", "alice-long-password");
        send(get("/api/v1/devices"), adminToken, null).andExpect(status().isOk());
        send(post("/api/v1/users"), adminToken, java.util.Map.of("username", "bob", "displayName", "Bob", "password", "bob-long-password"))
                .andExpect(status().isForbidden());

        // root can never be deleted
        String rootId = body(send(get("/api/v1/auth/me"), rootToken, null)).get("id").asText();
        send(delete("/api/v1/users/" + rootId), rootToken, null).andExpect(status().isBadRequest());

        // a new device after the baseline raises an alert; usage is aggregated in MB per day
        devices.observe("aa:bb:cc:dd:ee:01", "192.168.1.50", "laptop", true);   // baseline (trusted)
        devices.observe("aa:bb:cc:dd:ee:02", "192.168.1.51", "stranger", false); // new -> alert
        send(get("/api/v1/alerts?status=OPEN"), adminToken, null)
                .andExpect(jsonPath("$.items[0].type").value("NEW_DEVICE"));

        // ingest requires an API key
        send(post("/api/v1/ingest/usage"), adminToken, java.util.Map.of("samples", java.util.List.of())).andExpect(status().isUnauthorized());
        String key = body(send(post("/api/v1/settings/ingest/rotate-key"), adminToken, null).andExpect(status().isCreated())).get("apiKey").asText();
        var usage = java.util.Map.of("samples", java.util.List.of(java.util.Map.of("mac", "AA:BB:CC:DD:EE:01", "bytesDown", 3_145_728, "bytesUp", 1_048_576)));
        mvc.perform(post("/api/v1/ingest/usage").header("X-API-Key", key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(usage)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(1));
        send(get("/api/v1/usage/daily"), adminToken, null)
                .andExpect(jsonPath("$[0].megabytes").value(4.0));

        // external intrusion attempt becomes an alert, repeats are de-duplicated
        var sec = java.util.Map.of("events", java.util.List.of(java.util.Map.of("type", "INBOUND_CONNECTION_ATTEMPT", "sourceIp", "203.0.113.9", "destPort", 22, "protocol", "TCP")));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/ingest/security-events").header("X-API-Key", key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(sec)))
                    .andExpect(status().isOk());
        }
        send(get("/api/v1/alerts?type=EXTERNAL_CONNECTION_ATTEMPT"), adminToken, null)
                .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].occurrences").value(2));
    }
}
