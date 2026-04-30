package ru.ris.crackhash.worker.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.ris.crackhash.contract.xml.ManagerWorkerCrackRequest;
import ru.ris.crackhash.contract.xml.WorkerCancelRequest;
import ru.ris.crackhash.worker.service.WorkerCrackService;

@RestController
public class WorkerTaskController {

    private final WorkerCrackService workerCrackService;

    public WorkerTaskController(WorkerCrackService workerCrackService) {
        this.workerCrackService = workerCrackService;
    }

    @PostMapping(
            value = "/internal/api/worker/hash/crack/task",
            consumes = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<Void> acceptTask(@RequestBody ManagerWorkerCrackRequest task) {
        workerCrackService.submitTask(task);
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
