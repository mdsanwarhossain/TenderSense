package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.BidAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BidDecisionRequest(
        @NotNull BidAction action,
        @Size(max = 1024) String note,
        @Size(max = 128) String decidedBy) {
}
