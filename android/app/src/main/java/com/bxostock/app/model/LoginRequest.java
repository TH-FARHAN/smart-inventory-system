package com.bxostock.app.model;

public class LoginRequest {
    public String email;
    public String password;
    public String captchaId;
    public String captchaAnswer;

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}