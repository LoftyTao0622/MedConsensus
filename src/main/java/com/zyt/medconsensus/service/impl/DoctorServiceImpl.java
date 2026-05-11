package com.zyt.medconsensus.service.impl;

import com.zyt.medconsensus.dto.AuthResponse;
import com.zyt.medconsensus.dto.LoginRequest;
import com.zyt.medconsensus.dto.RegisterRequest;
import com.zyt.medconsensus.entity.DoctorBasicInfo;
import com.zyt.medconsensus.mapper.DoctorBasicInfoMapper;
import com.zyt.medconsensus.service.DoctorService;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DoctorServiceImpl implements DoctorService {

    private final DoctorBasicInfoMapper doctorBasicInfoMapper;
    private final PasswordEncoder passwordEncoder;

    public DoctorServiceImpl(DoctorBasicInfoMapper doctorBasicInfoMapper, PasswordEncoder passwordEncoder) {
        this.doctorBasicInfoMapper = doctorBasicInfoMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        if (doctorBasicInfoMapper.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        String phone = normalizePhone(request.getPhone());
        if (doctorBasicInfoMapper.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "手机号已注册");
        }

        DoctorBasicInfo doctor = new DoctorBasicInfo();
        doctor.setUsername(username);
        doctor.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        doctor.setPhone(phone);
        doctor.setDepartment(blankToNull(request.getDepartment()));
        doctor.setTitle(blankToNull(request.getTitle()));

        return toResponse(doctorBasicInfoMapper.save(doctor));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String phone = normalizePhone(request.getPhone());
        DoctorBasicInfo doctor = doctorBasicInfoMapper.findByPhone(phone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), doctor.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误");
        }

        return toResponse(doctor);
    }

    @Override
    public AuthResponse getCurrentUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }

        DoctorBasicInfo doctor = doctorBasicInfoMapper.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效"));

        return toResponse(doctor);
    }

    private AuthResponse toResponse(DoctorBasicInfo doctor) {
        return new AuthResponse(
                doctor.getId(),
                doctor.getUsername(),
                doctor.getPhone(),
                doctor.getDepartment(),
                doctor.getTitle(),
                doctor.getCreatedAt() != null ? doctor.getCreatedAt().toString() : null,
                doctor.getUpdatedAt() != null ? doctor.getUpdatedAt().toString() : null
        );
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        return phone == null ? null : phone.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
