package com.example.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TicketApiTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsAndReadsTicket() throws Exception {
        HttpResponse<String> response = send("POST", "/tickets", """
                {"title":"   GitLab  ","description":"  Access  denied\\nTry again  ",
                 "status":"RESOLVED","id":"client-id"}
                """);
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode ticket = json.readTree(response.body());
        String id = ticket.path("id").asText();
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(response.headers().firstValue("Location")).contains("/tickets/" + id);
        assertThat(ticket.path("title").asText()).isEqualTo("GitLab");
        assertThat(ticket.path("description").asText()).isEqualTo("Access  denied\nTry again");
        assertThat(ticket.path("status").asText()).isEqualTo("OPEN");
        assertThat(ticket.path("history").size()).isEqualTo(1);
        assertThat(time(ticket, "createdAt")).isEqualTo(time(ticket, "updatedAt"));
        assertEvent(ticket, 0, null, "OPEN", time(ticket, "createdAt"));
        assertThat(getTicket(id)).isEqualTo(ticket);
    }

    @Test
    void appliesOnlyAllowedTransitions() throws Exception {
        JsonNode opened = createTicket();
        String id = opened.path("id").asText();

        JsonNode conflict = assertError(patch(id, "RESOLVED"), 409, "INVALID_STATUS_TRANSITION");
        assertThat(conflict.path("details").path("currentStatus").asText()).isEqualTo("OPEN");
        assertThat(conflict.path("details").path("requestedStatus").asText()).isEqualTo("RESOLVED");
        assertThat(getTicket(id)).isEqualTo(opened);

        assertError(patch(id, "OPEN"), 409, "INVALID_STATUS_TRANSITION");
        assertThat(getTicket(id)).isEqualTo(opened);

        JsonNode working = assertStatus(patch(id, "IN_PROGRESS"), 200);
        assertThat(working.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(working.path("history").size()).isEqualTo(2);
        assertEvent(working, 1, "OPEN", "IN_PROGRESS", time(working, "updatedAt"));
        assertError(patch(id, "IN_PROGRESS"), 409, "INVALID_STATUS_TRANSITION");
        assertThat(getTicket(id)).isEqualTo(working);

        assertError(patch(id, "OPEN"), 409, "INVALID_STATUS_TRANSITION");
        assertThat(getTicket(id)).isEqualTo(working);

        JsonNode resolved = assertStatus(patch(id, "RESOLVED"), 200);
        assertThat(resolved.path("status").asText()).isEqualTo("RESOLVED");
        assertThat(time(resolved, "createdAt")).isEqualTo(time(opened, "createdAt"));
        assertThat(resolved.path("history").size()).isEqualTo(3);
        assertEvent(resolved, 0, null, "OPEN", time(opened, "createdAt"));
        assertEvent(resolved, 1, "OPEN", "IN_PROGRESS", time(working, "updatedAt"));
        assertEvent(resolved, 2, "IN_PROGRESS", "RESOLVED", time(resolved, "updatedAt"));
        assertError(patch(id, "OPEN"), 409, "INVALID_STATUS_TRANSITION");
        assertError(patch(id, "IN_PROGRESS"), 409, "INVALID_STATUS_TRANSITION");
        assertError(patch(id, "RESOLVED"), 409, "INVALID_STATUS_TRANSITION");
        assertThat(getTicket(id)).isEqualTo(resolved);
    }

    @Test
    void rejectsInvalidBodiesAndIdsBeforeLookingUpTicket() throws Exception {
        long count = jdbc.queryForObject("select count(*) from tickets", Long.class);
        JsonNode missingFields = assertError(send("POST", "/tickets", "{}"), 400, "VALIDATION_ERROR");
        assertThat(missingFields.path("details").has("title")).isTrue();
        assertThat(missingFields.path("details").has("description")).isTrue();
        JsonNode tooLong = assertError(send("POST", "/tickets", """
                {"title":"  ","description":"%s"}
                """.formatted("x".repeat(2001))), 400, "VALIDATION_ERROR");
        assertThat(tooLong.path("details").has("title")).isTrue();
        assertThat(tooLong.path("details").has("description")).isTrue();
        JsonNode unicode = assertError(send("POST", "/tickets", """
                {"title":"%s","description":"Valid"}
                """.formatted("😀".repeat(61))), 400, "VALIDATION_ERROR");
        assertThat(unicode.path("details").has("title")).isTrue();

        for (String body : new String[] { "", "{", "[]", "true", "null", """
                {"title":123,"description":"x"}
                """, """
                {"title":"x","description":false}
                """, """
                {"title":"x","description":"x"} {}
                """ }) {
            assertError(send("POST", "/tickets", body), 400, "INVALID_REQUEST");
        }
        HttpResponse<String> withoutContentType = client.send(request("/tickets")
                .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertError(withoutContentType, 400, "INVALID_REQUEST");
        HttpResponse<String> plainText = client.send(request("/tickets")
                .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("x")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertError(plainText, 415, "UNSUPPORTED_MEDIA_TYPE");

        String missingId = UUID.randomUUID().toString();
        JsonNode unknown = assertError(patch(missingId, "in_progress"), 400, "VALIDATION_ERROR");
        assertThat(unknown.path("details").has("status")).isTrue();
        assertError(send("PATCH", "/tickets/" + missingId + "/status", """
                {"status":true}
                """), 400, "INVALID_REQUEST");
        assertError(send("GET", "/tickets/not-a-uuid", null), 400, "INVALID_ID");
        assertError(send("GET", "/tickets/" + missingId, null), 404, "TICKET_NOT_FOUND");
        assertError(patch(missingId, "IN_PROGRESS"), 404, "TICKET_NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from tickets", Long.class)).isEqualTo(count);
    }

    @Test
    void concurrentRequestsRecordOneTransition() throws Exception {
        String id = createTicket().path("id").asText();
        HttpRequest request = jsonRequest("PATCH", "/tickets/" + id + "/status", """
                {"status":"IN_PROGRESS"}
                """);

        HttpResponse<String> firstResponse;
        HttpResponse<String> secondResponse;
        try (var connection = jdbc.getDataSource().getConnection();
                var lock = connection.prepareStatement("SELECT id FROM tickets WHERE id = ? FOR UPDATE")) {
            connection.setAutoCommit(false);
            lock.setObject(1, UUID.fromString(id));
            lock.executeQuery().close();

            var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            // Both requests must reach the locked row before either can finish.
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND wait_event_type = 'Lock'
                      AND query LIKE '%tickets%'
                    """, Long.class)).isEqualTo(2L));

            connection.commit();
            firstResponse = first.get(10, TimeUnit.SECONDS);
            secondResponse = second.get(10, TimeUnit.SECONDS);
        }
        assertThat(new int[] { firstResponse.statusCode(), secondResponse.statusCode() })
                .containsExactlyInAnyOrder(200, 409);
        assertError(firstResponse.statusCode() == 409 ? firstResponse : secondResponse,
                409, "INVALID_STATUS_TRANSITION");
        JsonNode ticket = getTicket(id);
        assertThat(ticket.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(ticket.path("history").size()).isEqualTo(2);
        assertEvent(ticket, 1, "OPEN", "IN_PROGRESS", time(ticket, "updatedAt"));
    }

    @Test
    void rollsBackStatusWhenHistoryInsertFails() throws Exception {
        JsonNode opened = createTicket();
        String id = opened.path("id").asText();
        jdbc.execute("""
                ALTER TABLE ticket_status_history ADD CONSTRAINT reject_test_transition
                CHECK (ticket_id <> '%s'::uuid OR to_status <> 'IN_PROGRESS')
                """.formatted(id));
        try {
            JsonNode error = assertError(patch(id, "IN_PROGRESS"), 500, "INTERNAL_ERROR");
            assertThat(error.path("details").size()).isZero();
            assertThat(getTicket(id)).isEqualTo(opened);
        } finally {
            jdbc.execute("ALTER TABLE ticket_status_history DROP CONSTRAINT reject_test_transition");
        }
    }

    @Test
    void acceptsBoundaryLengthsAndCreatesSeparateTickets() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "title", "  " + "x".repeat(120) + "  ",
                "description", "  " + "x".repeat(2000) + "  "));
        JsonNode first = assertStatus(send("POST", "/tickets", body), 201);
        JsonNode second = assertStatus(send("POST", "/tickets", body), 201);
        assertThat(first.path("title").asText()).hasSize(120);
        assertThat(first.path("description").asText()).hasSize(2000);
        assertThat(first.path("id")).isNotEqualTo(second.path("id"));
        assertThat(getTicket(first.path("id").asText())).isEqualTo(first);

        assertStatus(send("POST", "/tickets", """
                {"title":"x","description":"x"}
                """), 201);
        assertError(send("POST", "/tickets", json.writeValueAsString(Map.of(
                "title", "x".repeat(121), "description", "x"))), 400, "VALIDATION_ERROR");
    }

    @Test
    void invalidStatusBodiesLeaveExistingTicketUnchanged() throws Exception {
        JsonNode opened = createTicket();
        String id = opened.path("id").asText();
        for (String body : new String[] { "{}", "{\"status\":null}", "{\"status\":\"\"}",
                "{\"status\":\"UNKNOWN\"}" }) {
            assertError(send("PATCH", "/tickets/" + id + "/status", body), 400, "VALIDATION_ERROR");
            assertThat(getTicket(id)).isEqualTo(opened);
        }
        for (String body : new String[] { "[]", "{\"status\":12}", "{\"status\":[]}",
                "{\"status\":{}}" }) {
            assertError(send("PATCH", "/tickets/" + id + "/status", body), 400, "INVALID_REQUEST");
            assertThat(getTicket(id)).isEqualTo(opened);
        }
    }

    @Test
    void rollsBackCreationWhenInitialHistoryInsertFails() throws Exception {
        long ticketsBefore = jdbc.queryForObject("select count(*) from tickets", Long.class);
        long eventsBefore = jdbc.queryForObject("select count(*) from ticket_status_history", Long.class);
        jdbc.execute("""
                ALTER TABLE ticket_status_history ADD CONSTRAINT reject_test_creation
                CHECK (from_status IS NOT NULL) NOT VALID
                """);
        try {
            assertError(send("POST", "/tickets", """
                    {"title":"Rollback","description":"Initial history must be saved"}
                    """), 500, "INTERNAL_ERROR");
            assertThat(jdbc.queryForObject("select count(*) from tickets", Long.class)).isEqualTo(ticketsBefore);
            assertThat(jdbc.queryForObject("select count(*) from ticket_status_history", Long.class))
                    .isEqualTo(eventsBefore);
        } finally {
            jdbc.execute("ALTER TABLE ticket_status_history DROP CONSTRAINT reject_test_creation");
        }
    }

    @Test
    void ordersHistoryByIdWhenEventTimesAreEqual() throws Exception {
        String id = createTicket().path("id").asText();
        assertStatus(patch(id, "IN_PROGRESS"), 200);
        assertStatus(patch(id, "RESOLVED"), 200);
        jdbc.update("""
                UPDATE ticket_status_history SET changed_at = (
                    SELECT created_at FROM tickets WHERE id = ?)
                WHERE ticket_id = ?
                """, UUID.fromString(id), UUID.fromString(id));
        JsonNode ticket = getTicket(id);
        Instant createdAt = time(ticket, "createdAt");
        assertEvent(ticket, 0, null, "OPEN", createdAt);
        assertEvent(ticket, 1, "OPEN", "IN_PROGRESS", createdAt);
        assertEvent(ticket, 2, "IN_PROGRESS", "RESOLVED", createdAt);
    }

    private JsonNode createTicket() throws Exception {
        return assertStatus(send("POST", "/tickets", """
                {"title":"GitLab","description":"Access denied"}
                """), 201);
    }

    private JsonNode getTicket(String id) throws Exception {
        return assertStatus(send("GET", "/tickets/" + id, null), 200);
    }

    private HttpResponse<String> patch(String id, String status) throws Exception {
        return send("PATCH", "/tickets/" + id + "/status", """
                {"status":"%s"}
                """.formatted(status));
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest request = body == null ? request(path).GET().build() : jsonRequest(method, path, body);
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest jsonRequest(String method, String path, String body) {
        return request(path).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }

    private JsonNode assertStatus(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as("Response body: %s", response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        return json.readTree(response.body());
    }

    private JsonNode assertError(HttpResponse<String> response, int status, String code) throws Exception {
        JsonNode error = assertStatus(response, status);
        assertThat(error.path("code").asText()).isEqualTo(code);
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("details").isObject()).isTrue();
        return error;
    }

    private static void assertEvent(JsonNode ticket, int index, String from, String to, Instant changedAt) {
        JsonNode event = ticket.path("history").path(index);
        if (from == null) {
            assertThat(event.path("fromStatus").isNull()).isTrue();
        } else {
            assertThat(event.path("fromStatus").asText()).isEqualTo(from);
        }
        assertThat(event.path("toStatus").asText()).isEqualTo(to);
        assertThat(time(event, "changedAt")).isEqualTo(changedAt);
        assertThat(event.has("id")).isFalse();
    }

    private static Instant time(JsonNode object, String field) {
        String value = object.path(field).asText();
        assertThat(value).endsWith("Z");
        Instant time = Instant.parse(value);
        assertThat(time).isEqualTo(time.truncatedTo(ChronoUnit.MICROS));
        return time;
    }
}
