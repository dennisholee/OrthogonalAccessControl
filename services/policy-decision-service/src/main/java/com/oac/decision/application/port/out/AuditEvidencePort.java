package com.oac.decision.application.port.out;

import com.oac.decision.model.AuditEventRecord;

import java.util.List;

public interface AuditEvidencePort {

    void append(AuditEventRecord event);

    List<AuditEventRecord> findByEntityId(String entityId);

    List<AuditEventRecord> findAll();
}