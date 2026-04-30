package ru.ris.crackhash.manager.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import ru.ris.crackhash.contract.xml.ManagerWorkerCrackRequest;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;
import ru.ris.crackhash.manager.config.ManagerProperties;
import ru.ris.crackhash.manager.dto.CancelAllResponseDto;
import ru.ris.crackhash.manager.dto.HashStatusResponseDto;
import ru.ris.crackhash.manager.model.CrackRequestState;
import ru.ris.crackhash.manager.model.RequestStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class CrackHashService {

    public static final String DEFAULT_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final ManagerProperties managerProperties;
    private final WorkerHttpClient workerHttpClient;

    private final ConcurrentMap<String, CrackRequestState> requests = new ConcurrentHashMap<>();
    private final ExecutorService dispatchExecutor = Executors.newFixedThreadPool(8);
    private final ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(2);

    public CrackHashService(ManagerProperties managerProperties, WorkerHttpClient workerHttpClient) {
        this.managerProperties = managerProperties;
        this.workerHttpClient = workerHttpClient;
    }

    public String createRequest(String hash, int maxLength) {
        List<String> workers = managerProperties.getWorkers();
        String requestId = UUID.randomUUID().toString();
        int partCount = workers == null ? 0 : workers.size();
        CrackRequestState state = new CrackRequestState(partCount);
        requests.put(requestId, state);

        if (partCount == 0) {
            state.transitionIfInProgress(RequestStatus.ERROR_NO_WORKERS);
            return requestId;
        }

        long timeoutMillis = managerProperties.getRequestTimeout().toMillis();
        timeoutExecutor.schedule(() -> markTimeout(requestId), timeoutMillis, TimeUnit.MILLISECONDS);

        for (int partNumber = 0; partNumber < partCount; partNumber++) {
            String workerUrl = workers.get(partNumber);
            ManagerWorkerCrackRequest task = new ManagerWorkerCrackRequest(
                    requestId,
                    hash.toLowerCase(),
                    DEFAULT_ALPHABET,
                    maxLength,
                    partNumber,
                    partCount
            );

            CompletableFuture.runAsync(() -> sendTaskOrMarkError(requestId, workerUrl, task), dispatchExecutor);
        }

        return requestId;
    }

    public HashStatusResponseDto getStatus(String requestId) {
        CrackRequestState state = requests.get(requestId);
        if (state == null) {
            return new HashStatusResponseDto(RequestStatus.ERROR_NOT_FOUND, null);
        }
        return toStatusResponse(state);
    }

    public HashStatusResponseDto cancelRequest(String requestId) {
        CrackRequestState state = requests.get(requestId);
        if (state == null) {
            return new HashStatusResponseDto(RequestStatus.ERROR_NOT_FOUND, null);
        }

        boolean canceled = state.transitionIfInProgress(RequestStatus.CANCELED);
        if (canceled) {
            cancelRequestOnWorkers(requestId);
        }
        return toStatusResponse(state);
    }

    public CancelAllResponseDto cancelAllRequests() {
        List<String> canceledIds = new ArrayList<>();

        requests.forEach((requestId, state) -> {
            if (state.transitionIfInProgress(RequestStatus.CANCELED)) {
                canceledIds.add(requestId);
            }
        });

        for (String requestId : canceledIds) {
            cancelRequestOnWorkers(requestId);
        }

        Collections.sort(canceledIds);
        return new CancelAllResponseDto(canceledIds.size(), canceledIds);
    }

    public void acceptWorkerResponse(WorkerManagerCrackResponse response) {
        if (response == null || response.getRequestId() == null) {
            return;
        }

        CrackRequestState state = requests.get(response.getRequestId());
        if (state == null) {
            return;
        }

        if (!state.isInProgress()) {
            return;
        }

        if (response.isFailed()) {
            markStatusAndCancel(response.getRequestId(), RequestStatus.ERROR_WORKER_RESPONSE);
            return;
        }

        if (!state.isPartNumberValid(response.getPartNumber())) {
            markStatusAndCancel(response.getRequestId(), RequestStatus.ERROR_WORKER_RESPONSE);
            return;
        }

        state.acceptPartResult(response.getPartNumber(), response.getAnswers());
    }

    private void sendTaskOrMarkError(String requestId, String workerUrl, ManagerWorkerCrackRequest task) {
        try {
            workerHttpClient.sendCrackTask(workerUrl, task);
        } catch (Exception e) {
            markStatusAndCancel(requestId, RequestStatus.ERROR_DISPATCH);
        }
    }

    private void markTimeout(String requestId) {
        markStatusAndCancel(requestId, RequestStatus.TIMEOUT);
    }

    private void markStatusAndCancel(String requestId, RequestStatus status) {
        CrackRequestState state = requests.get(requestId);
        if (state != null && state.transitionIfInProgress(status)) {
            cancelRequestOnWorkers(requestId);
        }
    }

    private void cancelRequestOnWorkers(String requestId) {
        List<String> workers = managerProperties.getWorkers();
        if (workers == null || workers.isEmpty()) {
            return;
        }

        for (String workerUrl : workers) {
            CompletableFuture.runAsync(() -> {
                try {
                    workerHttpClient.cancelCrackTask(workerUrl, requestId);
                } catch (Exception ignored) {
                }
            }, dispatchExecutor);
        }
    }

    private HashStatusResponseDto toStatusResponse(CrackRequestState state) {
        RequestStatus status = state.getStatus();
        if (status == RequestStatus.READY) {
            return new HashStatusResponseDto(status, state.getSortedMatches());
        }
        return new HashStatusResponseDto(status, null);
    }

    @PreDestroy
    public void shutdownExecutors() {
        dispatchExecutor.shutdownNow();
        timeoutExecutor.shutdownNow();
    }
}
