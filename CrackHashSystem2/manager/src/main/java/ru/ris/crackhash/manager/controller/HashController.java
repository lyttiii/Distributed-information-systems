package ru.ris.crackhash.manager.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ris.crackhash.manager.dto.CancelAllResponseDto;
import ru.ris.crackhash.manager.dto.CrackHashRequestDto;
import ru.ris.crackhash.manager.dto.CrackHashResponseDto;
import ru.ris.crackhash.manager.dto.GenerateHashRequestDto;
import ru.ris.crackhash.manager.dto.GenerateHashResponseDto;
import ru.ris.crackhash.manager.dto.HashStatusResponseDto;
import ru.ris.crackhash.manager.service.CrackHashService;
import ru.ris.crackhash.manager.util.Md5Util;

@RestController
@RequestMapping("/api/hash")
public class HashController {

    private final CrackHashService crackHashService;

    public HashController(CrackHashService crackHashService) {
        this.crackHashService = crackHashService;
    }

    @PostMapping("/crack")
    public CrackHashResponseDto crackHash(@Valid @RequestBody CrackHashRequestDto request) {
        String requestId = crackHashService.createRequest(request.hash(), request.maxLength());
        return new CrackHashResponseDto(requestId);
    }

    @GetMapping("/status")
    public HashStatusResponseDto getStatus(@RequestParam String requestId) {
        return crackHashService.getStatus(requestId);
    }

    @PostMapping("/cancel")
    public HashStatusResponseDto cancelRequest(@RequestParam String requestId) {
        return crackHashService.cancelRequest(requestId);
    }

    @PostMapping("/cancel-all")
    public CancelAllResponseDto cancelAll() {
        return crackHashService.cancelAllRequests();
    }

    @PostMapping("/generate")
    public GenerateHashResponseDto generateHash(@Valid @RequestBody GenerateHashRequestDto request) {
        return new GenerateHashResponseDto(Md5Util.md5Hex(request.word()));
    }
}
