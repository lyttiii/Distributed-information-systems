package ru.ris.crackhash.manager.messaging;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;
import ru.ris.crackhash.manager.config.RabbitTopology;
import ru.ris.crackhash.manager.service.CrackHashService;

@Component
public class WorkerResultListener {

    private final CrackHashService crackHashService;

    public WorkerResultListener(CrackHashService crackHashService) {
        this.crackHashService = crackHashService;
    }

    @RabbitListener(
            queues = RabbitTopology.RESULT_QUEUE,
            containerFactory = "managerRabbitListenerContainerFactory"
    )
    public void handleWorkerResult(WorkerManagerCrackResponse payload, Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            crackHashService.acceptWorkerResponse(payload);
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            channel.basicNack(tag, false, true);
        }
    }
}
