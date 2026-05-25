package com.plp.lending.lms;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProgramLmsConfig {
    boolean lmsEnabled;
    String encoreProductCode;
}
