package com.turbotax.refund.domain.dto.request;

import com.turbotax.refund.domain.enums.IrsStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateFilingStatusRequest(@Schema(example = "FLAGGED") @NotNull IrsStatus irsStatus) {
}
