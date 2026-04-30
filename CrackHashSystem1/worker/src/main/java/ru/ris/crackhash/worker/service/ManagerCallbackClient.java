package ru.ris.crackhash.worker.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;
import ru.ris.crackhash.worker.config.WorkerProperties;

import java.util.List;

@Service
public class ManagerCallbackClient {

    private final RestTemplate managerRestTemplate;
    private final WorkerProperties workerProperties;

    public ManagerCallbackClient(RestTemplate managerRestTemplate, WorkerProperties workerProperties) {
        this.managerRestTemplate = managerRestTemplate;
        this.workerProperties = workerProperties;
    }

    public void sendResult(WorkerManagerCrackResponse payload) {
        String url = normalizeBaseUrl(workerProperties.getManagerBaseUrl()) + "/internal/api/manager/hash/crack/request";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));

        HttpEntity<WorkerManagerCrackResponse> requestEntity = new HttpEntity<>(payload, headers);

        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                managerRestTemplate.exchange(url, HttpMethod.PATCH, requestEntity, Void.class);
                return;
            } catch (RuntimeException ex) {
                lastException = ex;
                sleepQuietly(300L * attempt);
            }
        }

        if (lastException != null) {
            throw lastException;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
