package com.jumptotech.banking.audit;

import java.util.Map;
import java.util.UUID;

public interface AuditService {

    /**
     * Records an audit event in its own transaction (REQUIRES_NEW), so it is
     * persisted even when the caller's own business transaction rolls back -
     * e.g. a rejected transfer is still audited as a rejection. Only use this
     * when {@code actorUserId} (and any referenced entity) was already
     * committed by an earlier, separate transaction - REQUIRES_NEW opens a
     * new connection that cannot see rows your current, still-open
     * transaction hasn't committed yet.
     */
    void record(UUID actorUserId, String eventType, String entityType, UUID entityId,
                Map<String, Object> metadata, String ipAddress);

    /**
     * Records an audit event in the caller's own current transaction. Use
     * this when the audited subject (e.g. a newly created user) is being
     * created in that very same transaction - the event is only meaningful
     * if that transaction commits, and should roll back with it too.
     */
    void recordInCurrentTransaction(UUID actorUserId, String eventType, String entityType, UUID entityId,
                                     Map<String, Object> metadata, String ipAddress);
}
