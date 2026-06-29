package com.plp.program.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EarlyPayRepaymentUploadResult {
    private int processedCount;
    private String remarks;
    private List<String> errors = new ArrayList<>();
}
