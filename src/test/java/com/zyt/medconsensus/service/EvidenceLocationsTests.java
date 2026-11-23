package com.zyt.medconsensus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceLocationsTests {
    @Test
    void locatesExactTextAfterBlankPageAndRejectsOutOfScopeGuess() throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream writer = new PDPageContentStream(pdf, page)) {
                writer.beginText();
                writer.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                writer.newLineAtOffset(20, 200);
                writer.showText("CRP 24 mg/L");
                writer.endText();
            }
            pdf.save(bytes);
            var extracted = new ObjectMapper().readTree("""
                    {"keyMeasurements":["CRP elevated","Unlocated"],"citations":[
                     {"finding":"CRP elevated","page":1,"quote":"CRP 24 mg/L"},
                     {"finding":"Unlocated","page":90,"quote":"Impossible"}]}
                    """);
            var result = EvidenceLocations.locate("application/pdf", bytes.toByteArray(), extracted);
            assertThat(result.scope()).contains("2 页");
            assertThat(result.citations().get(0).page()).isEqualTo(2);
            assertThat(result.citations().get(0).quote()).isEqualTo("CRP 24 mg/L");
            assertThat(result.citations().get(1).page()).isNull();
        }
    }
}
