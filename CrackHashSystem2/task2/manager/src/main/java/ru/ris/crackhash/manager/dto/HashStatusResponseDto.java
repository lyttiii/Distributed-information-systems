package ru.ris.crackhash.manager.dto;

import ru.ris.crackhash.manager.model.RequestStatus;

import java.util.List;

public record HashStatusResponseDto(RequestStatus status, List<String> data) {
}
