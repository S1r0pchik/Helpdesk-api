package com.example.helpdesk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ticket_status_history")
class StatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketStatus toStatus;

    @Column(nullable = false)
    private Instant changedAt;

    protected StatusChange() {
    }

    StatusChange(Ticket ticket, TicketStatus fromStatus, TicketStatus toStatus, Instant changedAt) {
        this.ticket = ticket;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    TicketResponse.Event toResponse() {
        return new TicketResponse.Event(fromStatus, toStatus, changedAt);
    }
}
