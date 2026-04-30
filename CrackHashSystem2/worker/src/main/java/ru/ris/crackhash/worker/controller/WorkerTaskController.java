package ru.ris.crackhash.worker.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.ris.crackhash.contract.xml.ManagerWorkerCrackRequest;
import ru.ris.crackhash.contract.xml.WorkerCancelRequest;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;
import ru.ris.crackhash.worker.service.WorkerCrackService;
import ru.ris.crackhash.worker.service.WorkerResultPublisher;

@RestController
public class WorkerTaskController {

    private final WorkerCrackService workerCrackService;
    private final WorkerResultPublisher workerResultPublisher;

    public WorkerTaskController(WorkerCrackService workerCrackService, WorkerResultPublisher workerResultPublisher) {
        this.workerCrackService = workerCrackService;
        this.workerResultPublisher = workerResultPublisher;
    }

    @PostMapping(
            value = "/internal/api/worker/hash/crack/task",
            consumes = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<Void> acceptTask(@RequestBody ManagerWorkerCrackRequest task) {
        WorkerManagerCrackResponse result = workerCrackService.executeTask(task);
        if (result != null) {
            workerResultPublisher.publish(result);
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping(
            value = "/internal/api/worker/hash/crack/cancel",
            consumes = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<Void> cancelTask(@RequestBody WorkerCancelRequest request) {
        if (request.isCancelAll()) {
            workerCrackService.cancelAllTasks();
        } else if (request.getRequestId() != null && !request.getRequestId().isBlank()) {
            workerCrackService.cancelTask(request.getRequestId());
        }
        return ResponseEntity.accepted().build();
    }
}
