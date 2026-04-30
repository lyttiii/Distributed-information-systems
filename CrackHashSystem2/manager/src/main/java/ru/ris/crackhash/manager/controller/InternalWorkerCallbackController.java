package ru.ris.crackhash.manager.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;
import ru.ris.crackhash.manager.service.CrackHashService;

@RestController
public class InternalWorkerCallbackController {

    private final CrackHashService crackHashService;

    public InternalWorkerCallbackController(CrackHashService crackHashService) {
        this.crackHashService = crackHashService;
    }

    @PatchMapping(
            value = "/internal/api/manager/hash/crack/request",
            consumes = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<Void> receiveWorkerResult(@RequestBody WorkerManagerCrackResponse response) {
        crackHashService.acceptWorkerResponse(response);
        return ResponseEntity.ok().build();
    }
}
