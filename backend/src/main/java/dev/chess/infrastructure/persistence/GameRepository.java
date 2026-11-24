package dev.chess.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<StoredGame, UUID> {
    Optional<StoredGame> findByIdAndOwnerId(UUID id, UUID ownerId);
    Page<StoredGame> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId, Pageable pageable);
}
