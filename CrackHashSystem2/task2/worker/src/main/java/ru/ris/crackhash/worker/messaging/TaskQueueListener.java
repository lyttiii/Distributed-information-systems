package ru.ris.crackhash.worker.messaging;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.ris.crackhash.contract.xml.ManagerWorkerCrackRequest;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;
import ru.ris.crackhash.worker.config.RabbitTopology;
import ru.ris.crackhash.worker.service.WorkerCrackService;
import ru.ris.crackhash.worker.service.WorkerResultPublisher;

@Component
public class TaskQueueListener {

    private final WorkerCrackService workerCrackService;
    private final WorkerResultPublisher workerResultPublisher;

    public TaskQueueListener(WorkerCrackService workerCrackService, WorkerResultPublisher workerResultPublisher) {
        this.workerCrackService = workerCrackService;
        this.workerResultPublisher = workerResultPublisher;
    }

    @RabbitListener(
            queues = RabbitTopology.TASK_QUEUE,
            containerFactory = "workerRabbitListenerContainerFactory"
    )
    public void handleTask(ManagerWorkerCrackRequest task, Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            if (task == null || task.getRequestId() == null || task.getRequestId().isBlank()) {
                channel.basicAck(tag, false);
                return;
            }

            WorkerManagerCrackResponse result = workerCrackService.executeTask(task);
            if (result == null) {
                if (workerCrackService.isCancellationRequested(task.getRequestId())) {
                    // Task was intentionally canceled by manager; do not requeue.
                    channel.basicAck(tag, false);
                } else {
                    // Worker stopped/interrupted before producing result: return task to queue.
                    channel.basicNack(tag, false, true);
                }
                return;
            }

            workerResultPublisher.publish(result);
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            channel.basicNack(tag, false, true);
        }
    }
}
