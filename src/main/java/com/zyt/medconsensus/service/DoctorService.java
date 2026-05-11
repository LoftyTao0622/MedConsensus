package com.zyt.medconsensus.service;

import com.zyt.medconsensus.dto.AuthResponse;
import com.zyt.medconsensus.dto.LoginRequest;
import com.zyt.medconsensus.dto.RegisterRequest;

public interface DoctorService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse getCurrentUser(Long userId);
}
