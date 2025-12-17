package com.ExpenseTracker.app.user.persistence.repository;

import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserEntityRepository extends JpaRepository<UserEntity, UUID> {
}
