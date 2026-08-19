package com.example.order.repository;

import com.example.order.model.OutboxEvent;
import com.example.order.model.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    /** Oldest-first pending rows, for the relay to drain. */
    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxStatus status);

    /** Most-recently-published events first (for the UI event stream). */
    List<OutboxEvent> findTop20ByStatusOrderByIdDesc(OutboxStatus status);

    /** Most-recent events (both PENDING and PUBLISHED) first, ordered by createdAt DESC
     *  (true time order); id DESC is a stable tiebreaker for same-second events. */
    List<OutboxEvent> findTop20ByOrderByCreatedAtDescIdDesc();

    Optional<OutboxEvent> findByAggregateIdAndEventType(String aggregateId, String eventType);
}
