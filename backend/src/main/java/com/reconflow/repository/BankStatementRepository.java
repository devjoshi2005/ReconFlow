package com.reconflow.repository;

import com.reconflow.entity.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BankStatementRepository extends JpaRepository<BankStatement, UUID> {
}
