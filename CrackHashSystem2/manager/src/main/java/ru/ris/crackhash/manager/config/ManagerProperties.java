package ru.ris.crackhash.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "crackhash.manager")
public class ManagerProperties {

    private int partCount = 3;
    private List<String> workerIds = new ArrayList<>(List.of("worker1", "worker2", "worker3"));
    private Duration requestTimeout = Duration.ofSeconds(90);

    public int getPartCount() {
        return partCount;
    }

    public void setPartCount(int partCount) {
        this.partCount = partCount;
    }

    public List<String> getWorkerIds() {
        return workerIds;
    }

    public void setWorkerIds(List<String> workerIds) {
        this.workerIds = workerIds;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
