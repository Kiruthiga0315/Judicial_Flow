package com.judicialflow.common;

import com.judicialflow.common.models.Courtroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourtroomRepository extends JpaRepository<Courtroom, UUID>, JpaSpecificationExecutor<Courtroom> {

    Optional<Courtroom> findByName(String name);

    boolean existsByName(String name);
}
