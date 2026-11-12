package com.example.helpdesk;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Query("select t from Ticket t left join fetch t.history where t.id = :id")
    Optional<Ticket> findWithHistory(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findForUpdate(@Param("id") UUID id);
}
