package com.zyt.medconsensus.service.impl;

import com.zyt.medconsensus.dto.AuthResponse;
import com.zyt.medconsensus.dto.LoginRequest;
import com.zyt.medconsensus.dto.RegisterRequest;
import com.zyt.medconsensus.entity.Puser;
import com.zyt.medconsensus.mapper.PuserMapper;
import com.zyt.medconsensus.service.PuserService;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PuserServiceimpl implements PuserService {

    private final PuserMapper puserMapper;
    private final PasswordEncoder passwordEncoder;

    public PuserServiceimpl(PuserMapper puserMapper, PasswordEncoder passwordEncoder) {
        this.puserMapper = puserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        if (puserMapper.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }

        Puser user = new Puser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAge(request.getAge());
        user.setWeight(request.getWeight());
        user.setPhone(blankToNull(request.getPhone()));
        user.setGender(blankToNull(request.getGender()));

        return toResponse(puserMapper.save(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.getUsername());
        Puser user = puserMapper.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        return toResponse(user);
    }

    @Override
    public AuthResponse getCurrentUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }

        Puser user = puserMapper.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效"));

        return toResponse(user);
    }

    private AuthResponse toResponse(Puser user) {
        return new AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getAge(),
                user.getWeight().toPlainString(),
                user.getPhone(),
                user.getGender(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
                user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null
        );
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
