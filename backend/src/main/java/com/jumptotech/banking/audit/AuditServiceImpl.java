package com.jumptotech.banking.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditEventRepository auditEventRepository;

    public AuditServiceImpl(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorUserId, String eventType, String entityType, UUID entityId,
                        Map<String, Object> metadata, String ipAddress) {
        persist(actorUserId, eventType, entityType, entityId, metadata, ipAddress);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInCurrentTransaction(UUID actorUserId, String eventType, String entityType, UUID entityId,
                                            Map<String, Object> metadata, String ipAddress) {
        persist(actorUserId, eventType, entityType, entityId, metadata, ipAddress);
    }

    private void persist(UUID actorUserId, String eventType, String entityType, UUID entityId,
                          Map<String, Object> metadata, String ipAddress) {
        // Never log passwords, session tokens, or other sensitive auth material in metadata.
        try {
            auditEventRepository.save(new AuditEvent(actorUserId, eventType, entityType, entityId, metadata, ipAddress));
        } catch (Exception ex) {
            // Audit logging must never take down the primary business operation.
            log.error("Failed to persist audit event type={} entityType={} entityId={}", eventType, entityType, entityId, ex);
        }
    }
}
