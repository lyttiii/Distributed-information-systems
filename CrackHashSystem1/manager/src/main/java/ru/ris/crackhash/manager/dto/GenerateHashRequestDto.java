package ru.ris.crackhash.manager.dto;

import jakarta.validation.constraints.NotNull;

public record GenerateHashRequestDto(@NotNull String word) {
}
