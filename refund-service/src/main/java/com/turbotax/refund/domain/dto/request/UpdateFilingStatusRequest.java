package com.turbotax.refund.domain.dto.request;

import com.turbotax.refund.domain.enums.IrsStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFilingStatusRequest(@NotNull IrsStatus irsStatus) {
}
