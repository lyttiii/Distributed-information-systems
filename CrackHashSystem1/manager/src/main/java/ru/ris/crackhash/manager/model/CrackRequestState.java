package ru.ris.crackhash.manager.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CrackRequestState {

    private final int partCount;
    private final Set<Integer> completedParts = ConcurrentHashMap.newKeySet();
    private final Set<String> matches = ConcurrentHashMap.newKeySet();

    private RequestStatus status = RequestStatus.IN_PROGRESS;

    public CrackRequestState(int partCount) {
        this.partCount = partCount;
    }

    public synchronized boolean isInProgress() {
        return status == RequestStatus.IN_PROGRESS;
    }

    public synchronized boolean isPartNumberValid(int partNumber) {
        return partNumber >= 0 && partNumber < partCount;
    }

    public synchronized void acceptPartResult(int partNumber, List<String> words) {
        if (status != RequestStatus.IN_PROGRESS) {
            return;
        }
        if (!isPartNumberValid(partNumber)) {
            return;
        }
        if (!completedParts.add(partNumber)) {
            return;
        }
        if (words != null) {
            matches.addAll(words);
        }
        if (completedParts.size() == partCount) {
            status = RequestStatus.READY;
        }
    }

    public synchronized boolean transitionIfInProgress(RequestStatus nextStatus) {
        if (status == RequestStatus.IN_PROGRESS) {
            status = nextStatus;
            return true;
        }
        return false;
    }

    public synchronized RequestStatus getStatus() {
        return status;
    }

    public synchronized List<String> getSortedMatches() {
        List<String> sorted = new ArrayList<>(matches);
        Collections.sort(sorted);
        return sorted;
    }
}
