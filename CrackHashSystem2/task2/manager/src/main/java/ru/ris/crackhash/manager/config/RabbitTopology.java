package ru.ris.crackhash.manager.config;

public final class RabbitTopology {

    public static final String TASK_EXCHANGE = "crackhash.tasks.exchange";
    public static final String TASK_QUEUE = "crackhash.tasks.queue";
    public static final String TASK_ROUTING_KEY = "crackhash.task";

    public static final String RESULT_EXCHANGE = "crackhash.results.exchange";
    public static final String RESULT_QUEUE = "crackhash.results.queue";
    public static final String RESULT_ROUTING_KEY = "crackhash.result";
    public static final String CANCEL_EXCHANGE = "crackhash.cancel.exchange";
    public static final String CANCEL_ROUTING_KEY_PREFIX = "crackhash.cancel.";

    private RabbitTopology() {
    }
}
