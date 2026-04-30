package ru.ris.crackhash.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crackhash.worker")
public class WorkerProperties {

    private String managerBaseUrl = "http://manager:8080";

    public String getManagerBaseUrl() {
        return managerBaseUrl;
    }

    public void setManagerBaseUrl(String managerBaseUrl) {
        this.managerBaseUrl = managerBaseUrl;
    }
}
