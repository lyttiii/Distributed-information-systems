package ru.ris.crackhash.manager.persistence.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.ris.crackhash.manager.model.RequestStatus;
import ru.ris.crackhash.manager.persistence.document.HashRequestDocument;

import java.util.Collection;
import java.util.List;

public interface HashRequestRepository extends MongoRepository<HashRequestDocument, String> {
    List<HashRequestDocument> findByStatus(RequestStatus status);
    List<HashRequestDocument> findByStatusIn(Collection<RequestStatus> statuses);
}
