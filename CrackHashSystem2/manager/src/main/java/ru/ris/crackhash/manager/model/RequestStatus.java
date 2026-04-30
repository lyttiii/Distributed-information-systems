package ru.ris.crackhash.manager.model;

public enum RequestStatus {
    IN_PROGRESS,
    READY,
    TIMEOUT,
    CANCELED,
    ERROR_NO_WORKERS,
    ERROR_DISPATCH,
    ERROR_WORKER_RESPONSE,
    ERROR_NOT_FOUND;

    public boolean isTerminal() {
        return this != IN_PROGRESS;
    }

    public boolean isError() {
        return this.name().startsWith("ERROR_");
    }
}
