package com.zyt.medconsensus.service;

import com.zyt.medconsensus.dto.AuthResponse;
import com.zyt.medconsensus.dto.LoginRequest;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExternalIdentityService {

    private final JdbcTemplate jdbc;
    private final DoctorService accounts;

    public ExternalIdentityService(JdbcTemplate jdbc, DoctorService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public Optional<AuthResponse> lookup(String provider, String appId, String subject, String role) {
        validateIdentity(provider, appId, subject, role);
        return linkedId(provider, appId, subject, role).map(id -> accounts.getCurrentUser(id, role));
    }

    @Transactional
    public AuthResponse bind(String provider, String appId, String subject, String role, String phone, String password) {
        validateIdentity(provider, appId, subject, role);
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入本地账号的手机号和密码");
        }

        LoginRequest credentials = new LoginRequest();
        credentials.setRole(role);
        credentials.setPhone(phone);
        credentials.setPassword(password);
        AuthResponse account = accounts.login(credentials);
        boolean doctor = "DOCTOR".equals(role);
        int inserted = jdbc.update("""
                INSERT INTO external_identity(provider, app_id, subject, role, doctor_id, patient_id)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, provider, appId, subject, role, doctor ? account.id() : null, doctor ? null : account.id());

        // 重试允许相同绑定；任何一侧已绑定其他账号时保留原记录。
        if (inserted == 0 && linkedId(provider, appId, subject, role).filter(account.id()::equals).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该身份已有其他绑定");
        }
        return account;
    }

    private Optional<Long> linkedId(String provider, String appId, String subject, String role) {
        return jdbc.queryForList("""
                SELECT local_id FROM external_identity
                WHERE provider = ? AND app_id = ? AND subject = ? AND role = ?
                """, Long.class, provider, appId, subject, role).stream().findFirst();
    }

    private void validateIdentity(String provider, String appId, String subject, String role) {
        if (!StringUtils.hasText(provider) || provider.length() > 64
                || !StringUtils.hasText(appId) || appId.length() > 255
                || !StringUtils.hasText(subject) || subject.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "第三方身份无效");
        }
        if (!"DOCTOR".equals(role) && !"PATIENT".equals(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择医生或患者身份");
        }
    }
}
