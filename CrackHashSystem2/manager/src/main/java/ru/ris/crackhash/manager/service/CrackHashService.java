package ru.ris.crackhash.manager.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.ris.crackhash.contract.xml.ManagerWorkerCrackRequest;
import ru.ris.crackhash.contract.xml.WorkerCancelRequest;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;
import ru.ris.crackhash.manager.config.ManagerProperties;
import ru.ris.crackhash.manager.config.RabbitTopology;
import ru.ris.crackhash.manager.dto.CancelAllResponseDto;
import ru.ris.crackhash.manager.dto.HashStatusResponseDto;
import ru.ris.crackhash.manager.model.RequestStatus;
import ru.ris.crackhash.manager.persistence.document.HashRequestDocument;
import ru.ris.crackhash.manager.persistence.document.HashTaskDocument;
import ru.ris.crackhash.manager.persistence.document.TaskDispatchStatus;
import ru.ris.crackhash.manager.persistence.repository.HashRequestRepository;
import ru.ris.crackhash.manager.persistence.repository.HashTaskRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CrackHashService {

    public static final String DEFAULT_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final ManagerProperties managerProperties;
    private final RabbitTemplate rabbitTemplate;
    private final HashRequestRepository hashRequestRepository;
    private final HashTaskRepository hashTaskRepository;

    public CrackHashService(
            ManagerProperties managerProperties,
            RabbitTemplate rabbitTemplate,
            HashRequestRepository hashRequestRepository,
            HashTaskRepository hashTaskRepository
    ) {
        this.managerProperties = managerProperties;
        this.rabbitTemplate = rabbitTemplate;
        this.hashRequestRepository = hashRequestRepository;
        this.hashTaskRepository = hashTaskRepository;
    }

    public String createRequest(String hash, int maxLength) {
        String requestId = UUID.randomUUID().toString();
        int partCount = managerProperties.getPartCount();
        Instant now = Instant.now();

        HashRequestDocument request = new HashRequestDocument(
                requestId,
                hash.toLowerCase(),
                maxLength,
                partCount,
                RequestStatus.IN_PROGRESS,
                now,
                now
        );
        hashRequestRepository.save(request);

        if (partCount <= 0) {
            request.setStatus(RequestStatus.ERROR_DISPATCH);
            request.setUpdatedAt(Instant.now());
            hashRequestRepository.save(request);
            return requestId;
        }

        List<HashTaskDocument> tasks = buildTasks(requestId, hash.toLowerCase(), maxLength, partCount, now);
        hashTaskRepository.saveAll(tasks);
        tasks.forEach(this::dispatchTask);

        return requestId;
    }

    public HashStatusResponseDto getStatus(String requestId) {
        Optional<HashRequestDocument> requestOpt = hashRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            return new HashStatusResponseDto(RequestStatus.ERROR_NOT_FOUND, null);
        }
        return toStatusResponse(requestOpt.get());
    }

    public HashStatusResponseDto cancelRequest(String requestId) {
        Optional<HashRequestDocument> requestOpt = hashRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            return new HashStatusResponseDto(RequestStatus.ERROR_NOT_FOUND, null);
        }

        HashRequestDocument request = requestOpt.get();
        if (request.getStatus() == RequestStatus.IN_PROGRESS) {
            request.setStatus(RequestStatus.CANCELED);
            request.setUpdatedAt(Instant.now());
            hashRequestRepository.save(request);
            cancelPendingTasksInDb(requestId);
            notifyWorkersAboutCancel(requestId, false);
        }

        return toStatusResponse(request);
    }

    public CancelAllResponseDto cancelAllRequests() {
        List<String> canceledIds = new ArrayList<>();

        List<HashRequestDocument> inProgress = hashRequestRepository.findByStatus(RequestStatus.IN_PROGRESS);
        for (HashRequestDocument request : inProgress) {
            request.setStatus(RequestStatus.CANCELED);
            request.setUpdatedAt(Instant.now());
            hashRequestRepository.save(request);
            cancelPendingTasksInDb(request.getId());
            canceledIds.add(request.getId());
            notifyWorkersAboutCancel(request.getId(), false);
        }
        notifyWorkersAboutCancel(null, true);

        Collections.sort(canceledIds);
        return new CancelAllResponseDto(canceledIds.size(), canceledIds);
    }

    public void acceptWorkerResponse(WorkerManagerCrackResponse response) {
        if (response == null || response.getRequestId() == null) {
            return;
        }

        Optional<HashRequestDocument> requestOpt = hashRequestRepository.findById(response.getRequestId());
        if (requestOpt.isEmpty()) {
            return;
        }

        HashRequestDocument request = requestOpt.get();
        if (request.getStatus() != RequestStatus.IN_PROGRESS) {
            return;
        }

        int partNumber = response.getPartNumber();
        if (partNumber < 0 || partNumber >= request.getPartCount()) {
            markAsErrorAndCancel(request, RequestStatus.ERROR_WORKER_RESPONSE);
            return;
        }

        if (response.isFailed()) {
            requeueTask(request.getId(), partNumber, response.getErrorMessage());
            return;
        }

        if (request.getCompletedParts().contains(partNumber)) {
            return;
        }

        request.getCompletedParts().add(partNumber);
        if (response.getAnswers() != null) {
            request.getMatches().addAll(response.getAnswers());
        }

        request.setUpdatedAt(Instant.now());
        if (request.getCompletedParts().size() >= request.getPartCount()) {
            request.setStatus(RequestStatus.READY);
        }
        hashRequestRepository.save(request);

        hashTaskRepository.findByRequestIdAndPartNumber(request.getId(), partNumber).ifPresent(task -> {
            task.setState(TaskDispatchStatus.DONE);
            task.setUpdatedAt(Instant.now());
            task.setLastError(null);
            hashTaskRepository.save(task);
        });
    }

    @Scheduled(fixedDelayString = "${crackhash.manager.queue-retry-delay-ms:5000}")
    public void retryPendingQueueTasks() {
        List<HashTaskDocument> pendingTasks = hashTaskRepository.findByState(TaskDispatchStatus.PENDING_QUEUE);
        for (HashTaskDocument task : pendingTasks) {
            Optional<HashRequestDocument> requestOpt = hashRequestRepository.findById(task.getRequestId());
            if (requestOpt.isEmpty() || requestOpt.get().getStatus() != RequestStatus.IN_PROGRESS) {
                task.setState(TaskDispatchStatus.CANCELED);
                task.setUpdatedAt(Instant.now());
                hashTaskRepository.save(task);
                continue;
            }

            dispatchTask(task);
        }
    }

    @Scheduled(fixedDelayString = "${crackhash.manager.timeout-check-delay-ms:2000}")
    public void processTimeouts() {
        List<HashRequestDocument> inProgress = hashRequestRepository.findByStatus(RequestStatus.IN_PROGRESS);
        Instant now = Instant.now();
        long timeoutSeconds = managerProperties.getRequestTimeout().toSeconds();

        for (HashRequestDocument request : inProgress) {
            if (request.getCreatedAt() == null) {
                continue;
            }
            if (request.getCreatedAt().plusSeconds(timeoutSeconds).isBefore(now)) {
                request.setStatus(RequestStatus.TIMEOUT);
                request.setUpdatedAt(now);
                hashRequestRepository.save(request);
                cancelPendingTasksInDb(request.getId());
                notifyWorkersAboutCancel(request.getId(), false);
            }
        }
    }

    private List<HashTaskDocument> buildTasks(String requestId, String hash, int maxLength, int partCount, Instant now) {
        List<HashTaskDocument> tasks = new ArrayList<>(partCount);
        for (int partNumber = 0; partNumber < partCount; partNumber++) {
            HashTaskDocument task = new HashTaskDocument();
            task.setId(requestId + ":" + partNumber);
            task.setRequestId(requestId);
            task.setPartNumber(partNumber);
            task.setPartCount(partCount);
            task.setHash(hash);
            task.setMaxLength(maxLength);
            task.setAlphabet(DEFAULT_ALPHABET);
            task.setState(TaskDispatchStatus.PENDING_QUEUE);
            task.setDispatchAttempts(0);
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            tasks.add(task);
        }
        tasks.sort(Comparator.comparingInt(HashTaskDocument::getPartNumber));
        return tasks;
    }

    private void dispatchTask(HashTaskDocument task) {
        if (task.getState() == TaskDispatchStatus.DONE || task.getState() == TaskDispatchStatus.CANCELED) {
            return;
        }

        ManagerWorkerCrackRequest payload = new ManagerWorkerCrackRequest(
                task.getRequestId(),
                task.getHash(),
                task.getAlphabet(),
                task.getMaxLength(),
                task.getPartNumber(),
                task.getPartCount()
        );

        try {
            rabbitTemplate.convertAndSend(RabbitTopology.TASK_EXCHANGE, RabbitTopology.TASK_ROUTING_KEY, payload);
            task.setState(TaskDispatchStatus.QUEUED);
            task.setLastError(null);
        } catch (Exception ex) {
            task.setState(TaskDispatchStatus.PENDING_QUEUE);
            task.setLastError(shortError(ex));
        }

        task.setDispatchAttempts(task.getDispatchAttempts() + 1);
        task.setUpdatedAt(Instant.now());
        hashTaskRepository.save(task);
    }

    private void requeueTask(String requestId, int partNumber, String error) {
        hashTaskRepository.findByRequestIdAndPartNumber(requestId, partNumber).ifPresent(task -> {
            if (task.getState() == TaskDispatchStatus.CANCELED || task.getState() == TaskDispatchStatus.DONE) {
                return;
            }
            task.setState(TaskDispatchStatus.PENDING_QUEUE);
            task.setLastError(error);
            task.setUpdatedAt(Instant.now());
            hashTaskRepository.save(task);
        });
    }

    private void markAsErrorAndCancel(HashRequestDocument request, RequestStatus status) {
        if (request.getStatus() != RequestStatus.IN_PROGRESS) {
            return;
        }
        request.setStatus(status);
        request.setUpdatedAt(Instant.now());
        hashRequestRepository.save(request);
        cancelPendingTasksInDb(request.getId());
        notifyWorkersAboutCancel(request.getId(), false);
    }

    private void cancelPendingTasksInDb(String requestId) {
        List<HashTaskDocument> cancellable = hashTaskRepository.findByRequestIdAndStateIn(
                requestId,
                List.of(TaskDispatchStatus.PENDING_QUEUE, TaskDispatchStatus.QUEUED)
        );
        for (HashTaskDocument task : cancellable) {
            task.setState(TaskDispatchStatus.CANCELED);
            task.setUpdatedAt(Instant.now());
            hashTaskRepository.save(task);
        }
    }

    private void notifyWorkersAboutCancel(String requestId, boolean cancelAll) {
        List<String> workerIds = managerProperties.getWorkerIds();
        if (workerIds == null || workerIds.isEmpty()) {
            return;
        }

        WorkerCancelRequest cancelRequest = new WorkerCancelRequest(requestId, cancelAll);
        for (String workerId : workerIds) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitTopology.CANCEL_EXCHANGE,
                        RabbitTopology.CANCEL_ROUTING_KEY_PREFIX + workerId,
                        cancelRequest
                );
            } catch (Exception ignored) {
            }
        }
    }

    private HashStatusResponseDto toStatusResponse(HashRequestDocument state) {
        RequestStatus status = state.getStatus();
        if (status == RequestStatus.READY) {
            List<String> sorted = new ArrayList<>(state.getMatches());
            Collections.sort(sorted);
            return new HashStatusResponseDto(status, sorted);
        }
        return new HashStatusResponseDto(status, null);
    }

    private String shortError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 400 ? message.substring(0, 400) : message;
    }

}
