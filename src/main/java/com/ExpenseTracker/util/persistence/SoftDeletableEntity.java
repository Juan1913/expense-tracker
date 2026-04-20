package com.ExpenseTracker.util.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for entities that support soft deletion.
 * Adds a `deleted` flag (default false). Subclasses must still declare:
 *   @SQLDelete(sql = "UPDATE <table> SET deleted = true WHERE id = ?")
 *   @SQLRestriction("deleted = false")
 * so that JPA's remove() maps to an UPDATE and every query auto-filters
 * soft-deleted rows.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class SoftDeletableEntity {

    @Column(nullable = false)
    private boolean deleted = false;
}
