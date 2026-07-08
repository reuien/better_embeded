package com.mos.plc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plc")
public class PlcProperties {
    private String uploadRoot = "uploads";
    private String frontendDir = "../front/dist";
    private long deviceOnlineSeconds = 60;

    public String getUploadRoot() {
        return uploadRoot;
    }

    public void setUploadRoot(String uploadRoot) {
        this.uploadRoot = uploadRoot;
    }

    public String getFrontendDir() {
        return frontendDir;
    }

    public void setFrontendDir(String frontendDir) {
        this.frontendDir = frontendDir;
    }

    public long getDeviceOnlineSeconds() {
        return deviceOnlineSeconds;
    }

    public void setDeviceOnlineSeconds(long deviceOnlineSeconds) {
        this.deviceOnlineSeconds = deviceOnlineSeconds;
    }
}
