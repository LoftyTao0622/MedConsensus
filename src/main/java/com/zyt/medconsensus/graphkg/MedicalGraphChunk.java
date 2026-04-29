package com.zyt.medconsensus.graphkg;

public record MedicalGraphChunk(
        long id,
        String sourceFile,
        long sourceIndex,
        String chunkText
) {
}
