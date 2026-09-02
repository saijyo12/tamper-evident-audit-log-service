package com.auditlogservice.repository.impl;

import com.auditlogservice.model.AuditLogEntry;
import com.auditlogservice.repository.AuditLogRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Thread-safe ordered repository. It intentionally has no mutation or removal methods. */
@Repository
public class InMemoryAuditLogRepository implements AuditLogRepository {
    private final LinkedHashMap<UUID, AuditLogEntry> entries = new LinkedHashMap<>();

    @Override
    public synchronized AuditLogEntry append(AuditLogEntry entry) {
        entries.put(entry.id(), entry);
        return entry;
    }

    @Override
    public synchronized AuditLogEntry replace(AuditLogEntry entry) {
        if (!entries.containsKey(entry.id())) {
            throw new IllegalArgumentException("Cannot replace an audit event that does not exist");
        }
        entries.put(entry.id(), entry);
        return entry;
    }

    @Override
    public synchronized Optional<AuditLogEntry> findById(UUID id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public synchronized List<AuditLogEntry> findAll() {
        return List.copyOf(new ArrayList<>(entries.values()));
    }

    @Override
    public synchronized Optional<AuditLogEntry> findLatest() {
        return entries.isEmpty()
                ? Optional.empty()
                : Optional.of(new ArrayList<>(entries.values()).get(entries.size() - 1));
    }
}
