package com.zyt.medconsensus.graphkg;

import java.util.List;

public record MedicalGraphPath(
        String symptom,
        String disease,
        List<String> treatments,
        List<String> examinations,
        double confidence
) {
}
