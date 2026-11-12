package com.example.helpdesk;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tickets")
class Ticket {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<StatusChange> history = new ArrayList<>();

    protected Ticket() {
    }

    Ticket(String title, String description, Instant now) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.status = TicketStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
        recordStatusChange(null);
    }

    TicketStatus status() {
        return status;
    }

    void updateStatus(TicketStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    void recordStatusChange(TicketStatus previous) {
        history.add(new StatusChange(this, previous, status, updatedAt));
    }

    TicketResponse toResponse() {
        return new TicketResponse(id, title, description, status, createdAt, updatedAt,
                history.stream().map(StatusChange::toResponse).toList());
    }
}
