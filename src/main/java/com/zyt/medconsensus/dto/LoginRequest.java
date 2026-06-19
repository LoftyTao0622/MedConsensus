package com.zyt.medconsensus.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    private String role;

    @NotBlank
    private String phone;

    @NotBlank
    private String password;

    public String getPhone() {
        return phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
