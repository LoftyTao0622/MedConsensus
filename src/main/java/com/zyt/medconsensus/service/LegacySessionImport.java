package com.zyt.medconsensus.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.zyt.medconsensus.dto.DiagnosticResponse;
import com.zyt.medconsensus.dto.MessageHistoryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.dto.ChatSessionDto;
import com.zyt.medconsensus.mapper.ConsultationSessionStore;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LegacySessionImport implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacySessionImport.class);
    private static final String PREFIX = "medconsenus:collector:";
    private final StringRedisTemplate redis;
    private final ConsultationSessionStore sessions;
    private final ObjectMapper json;

    public LegacySessionImport(StringRedisTemplate redis, ConsultationSessionStore sessions, ObjectMapper json) {
        this.redis = redis;
        this.sessions = sessions;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) {
        String prefix = PREFIX + "sessions:";
        try (var keys = redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(100).build())) {
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    importIndex(key, Long.parseLong(key.substring(prefix.length())));
                } catch (NumberFormatException | JsonProcessingException invalidIndex) {
                    log.warn("旧版会话索引格式异常，跳过该索引");
                }
            }
        } catch (org.springframework.data.redis.RedisConnectionFailureException unavailable) {
            log.warn("历史 Redis 会话暂时无法迁移，将在下次启动时重试");
        }
    }

    private void importIndex(String key, long doctorId) throws JsonProcessingException {
        String index = redis.opsForValue().get(key);
        if (index == null) return;
        List<ChatSessionDto> items = json.readValue(index, new TypeReference<>() {});
        if (items == null) return;
        for (ChatSessionDto item : items) {
            if (item == null || item.id() == null || item.id().length() > 80 || item.title() == null || item.status() == null) continue;
            String suffix = doctorId + ":" + item.id();
            String messages = validJson(PREFIX + "memory:" + suffix, new TypeReference<List<MessageHistoryDto>>() {});
            String diagnosis = validJson(PREFIX + "diagnosis:" + suffix, new TypeReference<DiagnosticResponse>() {});
            sessions.importLegacy(doctorId, item, messages, diagnosis, legacyTime(item.updatedAt()));
        }
    }

    private <T> String validJson(String key, TypeReference<T> type) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) return null;
        try {
            return json.readValue(raw, type) == null ? null : raw;
        } catch (JsonProcessingException invalidSnapshot) {
            log.warn("旧版会话快照格式异常，保留其他可读取的问诊数据");
            return null;
        }
    }

    private OffsetDateTime legacyTime(String value) {
        OffsetDateTime now = OffsetDateTime.now();
        try {
            var parsed = DateTimeFormatter.ofPattern("MM-dd HH:mm").parse(value);
            LocalDateTime time = MonthDay.from(parsed).atYear(now.getYear()).atTime(LocalTime.from(parsed));
            if (time.isAfter(now.toLocalDateTime())) time = time.minusYears(1);
            return time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        } catch (DateTimeParseException | NullPointerException ignored) {
            return now;
        }
    }
}
