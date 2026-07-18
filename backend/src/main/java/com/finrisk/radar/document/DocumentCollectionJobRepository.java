package com.finrisk.radar.document;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentCollectionJobRepository
    extends JpaRepository<DocumentCollectionJob, UUID> {}
