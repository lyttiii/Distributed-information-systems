package ru.ris.crackhash.manager.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("hash_tasks")
public class HashTaskDocument {

    @Id
    private String id;

    private String requestId;
    private int partNumber;
    private int partCount;
    private String hash;
    private int maxLength;
    private String alphabet;
    private TaskDispatchStatus state;
    private int dispatchAttempts;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;

    public HashTaskDocument() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public int getPartCount() {
        return partCount;
    }

    public void setPartCount(int partCount) {
        this.partCount = partCount;
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

    public String getAlphabet() {
        return alphabet;
    }

    public void setAlphabet(String alphabet) {
        this.alphabet = alphabet;
    }

    public TaskDispatchStatus getState() {
        return state;
    }

    public void setState(TaskDispatchStatus state) {
        this.state = state;
    }

    public int getDispatchAttempts() {
        return dispatchAttempts;
    }

    public void setDispatchAttempts(int dispatchAttempts) {
        this.dispatchAttempts = dispatchAttempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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
