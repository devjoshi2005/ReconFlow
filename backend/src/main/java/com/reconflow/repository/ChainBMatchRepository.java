package com.reconflow.repository;

import com.reconflow.entity.ChainBMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChainBMatchRepository extends JpaRepository<ChainBMatch, UUID> {
}
