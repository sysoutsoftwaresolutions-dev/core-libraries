package com.core.common.event;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Shared repository to query and persist ProcessedEvent records in MongoDB.
 */
@Repository
public interface ProcessedEventRepository extends MongoRepository<ProcessedEvent, String> {
}
