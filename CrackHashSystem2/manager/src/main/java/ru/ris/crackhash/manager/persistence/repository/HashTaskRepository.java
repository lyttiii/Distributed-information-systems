package ru.ris.crackhash.manager.persistence.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.ris.crackhash.manager.persistence.document.HashTaskDocument;
import ru.ris.crackhash.manager.persistence.document.TaskDispatchStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HashTaskRepository extends MongoRepository<HashTaskDocument, String> {
    List<HashTaskDocument> findByState(TaskDispatchStatus state);
    List<HashTaskDocument> findByRequestId(String requestId);
    Optional<HashTaskDocument> findByRequestIdAndPartNumber(String requestId, int partNumber);
    List<HashTaskDocument> findByRequestIdAndStateIn(String requestId, Collection<TaskDispatchStatus> states);
}
