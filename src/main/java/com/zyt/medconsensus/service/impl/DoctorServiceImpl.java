package com.zyt.medconsensus.service.impl;

import com.zyt.medconsensus.dto.AuthResponse;
import com.zyt.medconsensus.dto.LoginRequest;
import com.zyt.medconsensus.dto.RegisterRequest;
import com.zyt.medconsensus.entity.DoctorBasicInfo;
import com.zyt.medconsensus.entity.PatientAccount;
import com.zyt.medconsensus.mapper.DoctorBasicInfoMapper;
import com.zyt.medconsensus.mapper.PatientAccountMapper;
import com.zyt.medconsensus.service.DoctorService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DoctorServiceImpl implements DoctorService {

    private final DoctorBasicInfoMapper doctorBasicInfoMapper;
    private final PatientAccountMapper patientAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public DoctorServiceImpl(
            DoctorBasicInfoMapper doctorBasicInfoMapper,
            PatientAccountMapper patientAccountMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.doctorBasicInfoMapper = doctorBasicInfoMapper;
        this.patientAccountMapper = patientAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if ("PATIENT".equals(normalizeRole(request.getRole()))) {
            return registerPatient(request);
        }

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
        doctor.setInviteCode(newInviteCode());

        return toDoctorResponse(doctorBasicInfoMapper.save(doctor));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        if ("PATIENT".equals(normalizeRole(request.getRole()))) {
            return loginPatient(request);
        }

        String phone = normalizePhone(request.getPhone());
        DoctorBasicInfo doctor = doctorBasicInfoMapper.findByPhone(phone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), doctor.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误");
        }

        return toDoctorResponse(ensureInviteCode(doctor));
    }

    @Override
    public AuthResponse getCurrentUser(Long userId, String role) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }

        if ("PATIENT".equals(normalizeRole(role))) {
            PatientAccount patient = patientAccountMapper.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效"));
            return toPatientResponse(patient);
        }

        DoctorBasicInfo doctor = doctorBasicInfoMapper.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效"));

        return toDoctorResponse(ensureInviteCode(doctor));
    }

    private AuthResponse registerPatient(RegisterRequest request) {
        String phone = normalizePhone(request.getPhone());
        if (patientAccountMapper.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该手机号已注册患者账号");
        }

        PatientAccount patient = new PatientAccount();
        patient.setPatientName(request.getUsername().trim());
        patient.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        patient.setPhone(phone);
        patient.setGender(blankToNull(request.getGender()));
        patient.setAge(request.getAge());
        patient.setWeight(request.getWeight());
        return toPatientResponse(patientAccountMapper.save(patient));
    }

    private AuthResponse loginPatient(LoginRequest request) {
        String phone = normalizePhone(request.getPhone());
        PatientAccount patient = patientAccountMapper.findByPhone(phone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误"));
        if (!passwordEncoder.matches(request.getPassword(), patient.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误");
        }
        return toPatientResponse(patient);
    }

    private AuthResponse toDoctorResponse(DoctorBasicInfo doctor) {
        return new AuthResponse(
                doctor.getId(),
                doctor.getUsername(),
                doctor.getPhone(),
                "DOCTOR",
                doctor.getDepartment(),
                doctor.getTitle(),
                null,
                null,
                null,
                doctor.getInviteCode(),
                doctor.getCreatedAt() != null ? doctor.getCreatedAt().toString() : null,
                doctor.getUpdatedAt() != null ? doctor.getUpdatedAt().toString() : null
        );
    }

    private AuthResponse toPatientResponse(PatientAccount patient) {
        return new AuthResponse(
                patient.getId(),
                patient.getPatientName(),
                patient.getPhone(),
                "PATIENT",
                null,
                null,
                patient.getGender(),
                patient.getAge(),
                patient.getWeight() == null ? null : patient.getWeight().toPlainString(),
                null,
                patient.getCreatedAt() != null ? patient.getCreatedAt().toString() : null,
                patient.getUpdatedAt() != null ? patient.getUpdatedAt().toString() : null
        );
    }

    private DoctorBasicInfo ensureInviteCode(DoctorBasicInfo doctor) {
        if (doctor.getInviteCode() == null || doctor.getInviteCode().isBlank()) {
            doctor.setInviteCode(newInviteCode());
            return doctorBasicInfoMapper.save(doctor);
        }
        return doctor;
    }

    private String newInviteCode() {
        String code;
        do {
            code = "DR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        } while (doctorBasicInfoMapper.existsByInviteCode(code));
        return code;
    }

    private String normalizeRole(String role) {
        String normalized = role == null || role.isBlank()
                ? "DOCTOR"
                : role.trim().toUpperCase(Locale.ROOT);
        if (!"DOCTOR".equals(normalized) && !"PATIENT".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的账号角色");
        }
        return normalized;
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
