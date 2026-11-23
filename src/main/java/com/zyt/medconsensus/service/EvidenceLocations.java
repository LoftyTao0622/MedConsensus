package com.zyt.medconsensus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zyt.medconsensus.dto.EvidenceSource.Citation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

record EvidenceLocations(String scope, List<Citation> citations) {
    static final int PDF_IMAGE_LIMIT = 4;

    static EvidenceLocations locate(String mediaType, byte[] bytes, JsonNode extracted) throws IOException {
        List<String> pageTexts = List.of();
        boolean image = mediaType.startsWith("image/");
        int visualPages = image ? 1 : 0;
        String scope = image ? "图片第 1 页" : "Word 正文前 12000 个字符，无固定页码";
        if ("application/pdf".equals(mediaType)) {
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                pageTexts = new ArrayList<>(document.getNumberOfPages());
                for (int page = 1; page <= document.getNumberOfPages(); page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    pageTexts.add(compact(stripper.getText(document)));
                }
                visualPages = Math.min(PDF_IMAGE_LIMIT, document.getNumberOfPages());
                scope = "PDF 共 " + document.getNumberOfPages() + " 页，图像识别第 1–" + visualPages
                        + " 页，另读取可提取正文前 6000 个字符";
            }
        }
        Map<String, JsonNode> references = new LinkedHashMap<>();
        extracted.path("citations").forEach(node -> references.putIfAbsent(node.path("finding").asText(), node));
        Map<String, Citation> findings = new LinkedHashMap<>();
        for (String field : List.of("keyMeasurements", "labFindings", "imagingFindings", "impression", "diagnosis")) {
            JsonNode items = extracted.path(field);
            List<String> values = new ArrayList<>();
            if (items.isTextual()) values.add(items.asText());
            else items.forEach(item -> values.add(item.asText("")));
            for (String finding : values) {
                if (finding.isBlank() || findings.containsKey(finding)) continue;
                JsonNode reference = references.get(finding);
                String quote = reference == null ? "" : reference.path("quote").asText("").trim();
                int hint = reference == null ? 0 : reference.path("page").asInt(0);
                String needle = compact(quote.isBlank() ? finding : quote);
                List<Integer> matches = new ArrayList<>();
                for (int i = 0; i < pageTexts.size(); i++) {
                    if (!needle.isBlank() && pageTexts.get(i).contains(needle)) matches.add(i + 1);
                }
                Integer page = null;
                String location = "未定位到原页";
                if (matches.size() == 1 || matches.contains(hint)) {
                    page = matches.size() == 1 ? matches.get(0) : Integer.valueOf(hint);
                    location = "原文已匹配";
                    if (quote.isBlank()) quote = finding;
                } else if (image || (!quote.isBlank() && hint > 0 && hint <= visualPages)) {
                    page = image ? 1 : hint;
                    location = "图像识别，请对照原件";
                }
                findings.put(finding, new Citation(finding, quote, page, location));
            }
        }
        return new EvidenceLocations(scope, List.copyOf(findings.values()));
    }

    private static String compact(String value) {
        return value.replaceAll("[\\p{Z}\\s]+", "");
    }
}
