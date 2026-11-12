package com.example.helpdesk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record TicketResponse(UUID id, String title, String description, TicketStatus status,
        Instant createdAt, Instant updatedAt, List<Event> history) {

    record Event(TicketStatus fromStatus, TicketStatus toStatus, Instant changedAt) {
    }
}
