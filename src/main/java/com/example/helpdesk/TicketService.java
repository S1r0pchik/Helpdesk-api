package com.example.helpdesk;

import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
class TicketService {

    private final TicketRepository tickets;
    private final EntityManager entityManager;

    TicketService(TicketRepository tickets, EntityManager entityManager) {
        this.tickets = tickets;
        this.entityManager = entityManager;
    }

    @Transactional
    public TicketResponse create(String title, String description) {
        Ticket ticket = new Ticket(title, description, now());
        entityManager.persist(ticket);
        return ticket.toResponse();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(UUID id) {
        return tickets.findWithHistory(id)
                .orElseThrow(TicketService::notFound)
                .toResponse();
    }

    @Transactional
    public TicketResponse changeStatus(UUID id, TicketStatus target) {
        Ticket ticket = tickets.findForUpdate(id).orElseThrow(TicketService::notFound);
        TicketStatus previous = ticket.status();
        if (!previous.canChangeTo(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                    "Cannot transition ticket from " + previous + " to " + target,
                    Map.of("currentStatus", previous.name(), "requestedStatus", target.name()));
        }

        ticket.updateStatus(target, now());
        // Send UPDATE first. A failed history insert still rolls back the whole
        // transaction.
        entityManager.flush();
        ticket.recordStatusChange(previous);
        return ticket.toResponse();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket not found");
    }
}
