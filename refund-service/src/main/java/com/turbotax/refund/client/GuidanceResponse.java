package com.turbotax.refund.client;

import java.util.List;

public record GuidanceResponse(String situationKey, String narrative, List<GuidanceDoc> sources) {}
