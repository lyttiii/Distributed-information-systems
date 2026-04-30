package ru.ris.crackhash.manager.dto;

import java.util.List;

public record CancelAllResponseDto(int canceledCount, List<String> requestIds) {
}
