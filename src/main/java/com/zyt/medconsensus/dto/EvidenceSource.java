package com.zyt.medconsensus.dto;

import java.util.List;

public record EvidenceSource(String id, String fileName, String scope, List<Citation> citations,
                             String originalText, String reviewedText) {
    public record Citation(String finding, String quote, Integer page, String location) {}
}
