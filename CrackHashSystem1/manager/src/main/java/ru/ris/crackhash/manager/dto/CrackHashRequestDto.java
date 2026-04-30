package ru.ris.crackhash.manager.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CrackHashRequestDto(
        @NotBlank
        @Pattern(regexp = "^[a-fA-F0-9]{32}$")
        String hash,
        @Min(1)
        @Max(8)
        int maxLength
) {
}
