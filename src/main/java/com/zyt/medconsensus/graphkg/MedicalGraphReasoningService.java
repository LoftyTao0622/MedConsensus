package com.zyt.medconsensus.graphkg;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MedicalGraphReasoningService {

    private final MedicalKnowledgeGraphRepository repository;

    public MedicalGraphReasoningService(MedicalKnowledgeGraphRepository repository) {
        this.repository = repository;
    }

    public List<MedicalGraphPath> reasonBySymptoms(List<String> symptoms) {
        try {
            return repository.findSymptomDiseaseTreatmentPaths(symptoms, 10);
        } catch (Exception exception) {
            return List.of();
        }
    }
}
