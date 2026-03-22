package com.blade.auth.dto;

import java.util.List;

/**
 * VbenAdmin 用户信息VO
 */
public class UserInfoVO {
    private String userId;
    private String username;
    private String realName;
    private String avatar;
    private String desc;
    private String homePath;
    private List<String> roles;
    private String token;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getHomePath() { return homePath; }
    public void setHomePath(String homePath) { this.homePath = homePath; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}