package com.oac.decision.adapter.out.audit;

import com.oac.decision.application.port.out.AuditEvidencePort;
import com.oac.decision.model.AuditEventRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class InMemoryAuditEvidenceAdapter implements AuditEvidencePort {

    private final List<AuditEventRecord> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(AuditEventRecord event) {
        events.add(event);
    }

    /** Clears all accumulated events (used between test scenarios). */
    public void clear() {
        events.clear();
    }

    @Override
    public List<AuditEventRecord> findByEntityId(String entityId) {
        return events.stream()
                .filter(event -> entityId.equals(event.entityId()))
                .toList();
    }

    @Override
    public List<AuditEventRecord> findAll() {
        return new ArrayList<>(events);
    }
}