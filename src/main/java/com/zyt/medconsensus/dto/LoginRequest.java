package com.zyt.medconsensus.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "请选择医生或患者身份")
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
