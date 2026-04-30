package ru.ris.crackhash.manager.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import ru.ris.crackhash.manager.model.RequestStatus;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Document("hash_requests")
public class HashRequestDocument {

    @Id
    private String id;

    @Version
    private Long version;

    private String hash;
    private int maxLength;
    private int partCount;
    private RequestStatus status;
    private Set<Integer> completedParts = new HashSet<>();
    private Set<String> matches = new HashSet<>();
    private Instant createdAt;
    private Instant updatedAt;

    public HashRequestDocument() {
    }

    public HashRequestDocument(String id, String hash, int maxLength, int partCount, RequestStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.hash = hash;
        this.maxLength = maxLength;
        this.partCount = partCount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public int getPartCount() {
        return partCount;
    }

    public void setPartCount(int partCount) {
        this.partCount = partCount;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public Set<Integer> getCompletedParts() {
        return completedParts;
    }

    public void setCompletedParts(Set<Integer> completedParts) {
        this.completedParts = completedParts;
    }

    public Set<String> getMatches() {
        return matches;
    }

    public void setMatches(Set<String> matches) {
        this.matches = matches;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
