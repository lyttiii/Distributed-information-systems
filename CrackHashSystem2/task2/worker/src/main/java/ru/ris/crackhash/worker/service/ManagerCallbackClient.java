package ru.ris.crackhash.worker.service;

import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;

@Deprecated(forRemoval = true)
public final class ManagerCallbackClient {

    private ManagerCallbackClient() {
    }

    public static void sendResult(WorkerManagerCrackResponse payload) {
        // Replaced by RabbitMQ result publishing.
    }
}
