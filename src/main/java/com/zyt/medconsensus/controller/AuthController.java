package com.zyt.medconsensus.controller;

import com.zyt.medconsensus.dto.AuthResponse;
import com.zyt.medconsensus.dto.LoginRequest;
import com.zyt.medconsensus.dto.RegisterRequest;
import com.zyt.medconsensus.service.DoctorService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String SESSION_USER_ID = "CURRENT_USER_ID";
    private static final String SESSION_USER_ROLE = "CURRENT_USER_ROLE";

    private final DoctorService doctorService;

    public AuthController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        AuthResponse response = doctorService.register(request);
        session.setAttribute(SESSION_USER_ID, response.id());
        session.setAttribute(SESSION_USER_ROLE, response.role());
        return response;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        AuthResponse response = doctorService.login(request);
        session.setAttribute(SESSION_USER_ID, response.id());
        session.setAttribute(SESSION_USER_ROLE, response.role());
        return response;
    }

    @GetMapping("/me")
    public AuthResponse me(HttpSession session) {
        Object userId = session.getAttribute(SESSION_USER_ID);
        Object role = session.getAttribute(SESSION_USER_ROLE);
        AuthResponse response = doctorService.getCurrentUser(
                userId instanceof Long ? (Long) userId : null,
                role instanceof String ? (String) role : null
        );
        session.setAttribute(SESSION_USER_ROLE, response.role());
        return response;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("success", true, "message", "已退出登录");
    }
}
