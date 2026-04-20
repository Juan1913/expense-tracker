package com.ExpenseTracker.app.account.persistence.repository;

import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountEntityRepository extends JpaRepository<AccountEntity, UUID> {

    List<AccountEntity> findByUser_Id(UUID userId);

    Optional<AccountEntity> findByIdAndUser_Id(UUID id, UUID userId);

    /**
     * Filtered account listing.
     * `searchPattern`: lowercase LIKE pattern ("%foo%" or "%%" for no filter),
     *                  built by the service layer.
     * `currency`: exact match (e.g. "COP"), pass null to skip.
     * `sort`: applied by caller (service passes Spring Data Sort).
     */
    @Query("SELECT a FROM AccountEntity a WHERE a.user.id = :userId " +
           "AND (LOWER(a.name)                        LIKE :searchPattern " +
           "     OR LOWER(a.bank)                     LIKE :searchPattern " +
           "     OR LOWER(COALESCE(a.description, '')) LIKE :searchPattern " +
           "     OR LOWER(COALESCE(a.cardNumber, ''))  LIKE :searchPattern) " +
           "AND (:currency IS NULL OR a.currency = :currency)")
    List<AccountEntity> findByUserFiltered(@Param("userId") UUID userId,
                                            @Param("searchPattern") String searchPattern,
                                            @Param("currency") String currency,
                                            Sort sort);

    // ── Trash helpers (unchanged) ──
    @Query(value = "SELECT * FROM accounts WHERE user_id = :userId AND deleted = true ORDER BY created_at DESC",
           nativeQuery = true)
    List<AccountEntity> findDeletedByUser(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM accounts WHERE id = :id AND user_id = :userId",
           nativeQuery = true)
    Optional<AccountEntity> findByIdAndUserIncludingDeleted(@Param("id") UUID id,
                                                            @Param("userId") UUID userId);

    @Modifying
    @Query(value = "UPDATE accounts SET deleted = false WHERE id = :id AND user_id = :userId",
           nativeQuery = true)
    int restoreByIdAndUser(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM accounts WHERE id = :id AND user_id = :userId",
           nativeQuery = true)
    int hardDeleteByIdAndUser(@Param("id") UUID id, @Param("userId") UUID userId);
}
