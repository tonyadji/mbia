package com.lehnade.mbia;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * The emails caught by the Mailpit container of {@link TestcontainersConfiguration}, read through
 * the Mailpit API. Emails are sent before the API answers (OQ-055), so no waiting is needed.
 */
public final class MailpitClient {

    private final RestClient api;

    MailpitClient(URI baseUrl) {
        this.api = RestClient.create(baseUrl.toString());
    }

    /** An email as the recipient reads it. */
    public record Email(String subject, String from, String text, String html) {}

    /** @return the emails sent to this address, most recent first */
    public List<Email> emailsTo(String address) {
        JsonNode found = api.get()
                .uri(uri -> uri.path("/api/v1/search").queryParam("query", "to:\"{address}\"").build(address))
                .retrieve()
                .body(JsonNode.class);
        List<Email> emails = new ArrayList<>();
        for (JsonNode summary : found.get("messages")) {
            JsonNode message = api.get().uri("/api/v1/message/{id}", summary.get("ID").asString())
                    .retrieve()
                    .body(JsonNode.class);
            emails.add(new Email(message.get("Subject").asString(), message.get("From").get("Address").asString(),
                    message.get("Text").asString(), message.get("HTML").asString()));
        }
        return emails;
    }

    /** @return the only email sent to this address, if any */
    public Optional<Email> lastEmailTo(String address) {
        return emailsTo(address).stream().findFirst();
    }

    /**
     * Makes Mailpit refuse every email with an SMTP error (Mailpit chaos), or accept them again: the
     * mail provider "down" of OQ-055.
     */
    public void refuseEmails(boolean refuse) {
        api.put().uri("/api/v1/chaos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("Recipient", Map.of("ErrorCode", 451, "Probability", refuse ? 100 : 0)))
                .retrieve()
                .toBodilessEntity();
    }
}
