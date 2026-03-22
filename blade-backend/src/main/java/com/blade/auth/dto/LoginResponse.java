package com.blade.auth.dto;

public class LoginResponse {
    private String token;
    private String accessToken;  // vben-admin 前端期望的字段名
    private String refreshToken;
    private long expiresIn;

    public LoginResponse() {}

    public LoginResponse(String token, String refreshToken, long expiresIn) {
        this.token = token;
        this.accessToken = token;  // 保持一致
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
