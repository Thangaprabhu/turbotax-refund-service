package com.turbotax.ai.guidance;

import java.util.List;

public record GuidanceResponse(String situationKey, String narrative, List<GuidanceDoc> sources) {}
