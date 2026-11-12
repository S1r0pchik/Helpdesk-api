package com.example.helpdesk;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/tickets")
class TicketController {

    private final TicketService service;

    TicketController(TicketService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicket request) {
        TicketResponse ticket = service.create(request.title(), request.description());
        return ResponseEntity.created(URI.create("/tickets/" + ticket.id())).body(ticket);
    }

    @GetMapping("/{id}")
    TicketResponse get(@PathVariable String id) {
        return service.get(parseId(id));
    }

    @PatchMapping("/{id}/status")
    TicketResponse changeStatus(@PathVariable String id, @Valid @RequestBody ChangeStatus request) {
        return service.changeStatus(parseId(id), parseStatus(request.status()));
    }

    private static UUID parseId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equalsIgnoreCase(value)) {
                return id;
            }
        } catch (IllegalArgumentException ignored) {
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "Ticket ID must be a UUID");
    }

    private static TicketStatus parseStatus(String value) {
        try {
            return TicketStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(Map.of("status", "Unknown status"));
        }
    }

    record CreateTicket(
            @NotNull(message = "Required") @Size(min = 1, max = 120, message = "Must contain 1 to 120 characters") String title,
            @NotNull(message = "Required") @Size(min = 1, max = 2000, message = "Must contain 1 to 2000 characters") String description) {

        CreateTicket {
            title = title == null ? null : title.strip();
            description = description == null ? null : description.strip();
        }
    }

    record ChangeStatus(@NotNull(message = "Required") String status) {
    }
}
