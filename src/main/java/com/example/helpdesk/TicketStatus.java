package com.example.helpdesk;

enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED;

    boolean canChangeTo(TicketStatus target) {
        return switch (this) {
            case OPEN -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == RESOLVED;
            case RESOLVED -> false;
        };
    }
}
