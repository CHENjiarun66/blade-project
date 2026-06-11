package com.blade.agent.auth;

public class AgentCallAuditEvent {

    private String method;
    private String path;
    private String queryString;
    private Integer status;
    private Long durationMs;
    private String ip;
    private String userAgent;

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getQueryString() { return queryString; }
    public void setQueryString(String queryString) { this.queryString = queryString; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getRawKey() {
        return null;
    }
}
