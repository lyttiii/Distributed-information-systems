package ru.ris.crackhash.manager.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.ris.crackhash.contract.xml.ManagerWorkerCrackRequest;
import ru.ris.crackhash.contract.xml.WorkerCancelRequest;

import java.util.List;

@Service
public class WorkerHttpClient {

    private final RestTemplate workerRestTemplate;

    public WorkerHttpClient(RestTemplate workerRestTemplate) {
        this.workerRestTemplate = workerRestTemplate;
    }

    public void sendCrackTask(String workerBaseUrl, ManagerWorkerCrackRequest payload) {
        String url = normalizeBaseUrl(workerBaseUrl) + "/internal/api/worker/hash/crack/task";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));

        HttpEntity<ManagerWorkerCrackRequest> requestEntity = new HttpEntity<>(payload, headers);
        workerRestTemplate.exchange(url, HttpMethod.POST, requestEntity, Void.class);
    }

    public void cancelCrackTask(String workerBaseUrl, String requestId) {
        String url = normalizeBaseUrl(workerBaseUrl) + "/internal/api/worker/hash/crack/cancel";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));

        WorkerCancelRequest payload = new WorkerCancelRequest(requestId, false);
        HttpEntity<WorkerCancelRequest> requestEntity = new HttpEntity<>(payload, headers);
        workerRestTemplate.exchange(url, HttpMethod.POST, requestEntity, Void.class);
    }

    public void cancelAllCrackTasks(String workerBaseUrl) {
        String url = normalizeBaseUrl(workerBaseUrl) + "/internal/api/worker/hash/crack/cancel";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));

        WorkerCancelRequest payload = new WorkerCancelRequest(null, true);
        HttpEntity<WorkerCancelRequest> requestEntity = new HttpEntity<>(payload, headers);
        workerRestTemplate.exchange(url, HttpMethod.POST, requestEntity, Void.class);
    }

    private String normalizeBaseUrl(String workerBaseUrl) {
        if (workerBaseUrl.endsWith("/")) {
            return workerBaseUrl.substring(0, workerBaseUrl.length() - 1);
        }
        return workerBaseUrl;
    }
}
