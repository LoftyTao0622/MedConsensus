package com.zyt.medconsensus.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.dto.ChatSessionDto;
import com.zyt.medconsensus.mapper.ConsultationSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LegacySessionImportTests {
    @Test
    @SuppressWarnings("unchecked")
    void skipsBrokenIndicesAndPreservesUsablePartsOfLaterSessions() {
        var redis = mock(StringRedisTemplate.class);
        var sessions = mock(ConsultationSessionStore.class);
        Cursor<String> keys = mock(Cursor.class);
        ValueOperations<String,String> value = mock(ValueOperations.class);
        String prefix = "medconsenus:collector:";
        when(redis.scan(any())).thenReturn(keys);
        when(redis.opsForValue()).thenReturn(value);
        when(keys.hasNext()).thenReturn(true,true,true,false);
        when(keys.next()).thenReturn(prefix+"sessions:bad",prefix+"sessions:1",prefix+"sessions:2");
        when(value.get(prefix+"sessions:1")).thenReturn("{invalid");
        when(value.get(prefix+"sessions:2")).thenReturn("""
                [{"id":"s","title":"旧会诊","status":"待医生定夺","updatedAt":"11-01 09:30","evidenceFileName":null}]
                """);
        when(value.get(prefix+"memory:2:s")).thenReturn("{invalid");
        var diagnosis = """
                {"conclusion":"初诊","confidence":0.5,"riskLevel":"待定","structuredAnalysis":[],"suggestions":[],"reviewers":[]}
                """;
        when(value.get(prefix+"diagnosis:2:s")).thenReturn(diagnosis);

        new LegacySessionImport(redis,sessions,new ObjectMapper()).run(null);

        verify(sessions).importLegacy(eq(2L),eq(new ChatSessionDto("s","旧会诊","待医生定夺","11-01 09:30",null)),
                isNull(),eq(diagnosis),any());
    }
}
