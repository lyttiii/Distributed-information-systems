package ru.ris.crackhash.worker.messaging;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.ris.crackhash.contract.xml.WorkerCancelRequest;
import ru.ris.crackhash.worker.service.WorkerCrackService;

@Component
public class CancelQueueListener {

    private final WorkerCrackService workerCrackService;

    public CancelQueueListener(WorkerCrackService workerCrackService) {
        this.workerCrackService = workerCrackService;
    }

    @RabbitListener(
            queues = "#{@workerCancelQueueName}",
            containerFactory = "workerRabbitListenerContainerFactory"
    )
    public void handleCancel(WorkerCancelRequest request, Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            if (request != null) {
                if (request.isCancelAll()) {
                    workerCrackService.cancelAllTasks();
                } else if (request.getRequestId() != null && !request.getRequestId().isBlank()) {
                    workerCrackService.cancelTask(request.getRequestId());
                }
            }
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            channel.basicNack(tag, false, true);
        }
    }
}
