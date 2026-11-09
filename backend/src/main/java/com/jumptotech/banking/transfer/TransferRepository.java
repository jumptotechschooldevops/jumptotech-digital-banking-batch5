package com.jumptotech.banking.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByIdempotencyKey(UUID idempotencyKey);

    List<Transfer> findTop10ByOrderByCreatedAtDesc();
}
