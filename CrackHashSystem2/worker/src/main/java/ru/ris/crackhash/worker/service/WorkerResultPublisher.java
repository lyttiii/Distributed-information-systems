package ru.ris.crackhash.worker.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;
import ru.ris.crackhash.worker.config.RabbitTopology;

@Service
public class WorkerResultPublisher {

    private final RabbitTemplate rabbitTemplate;

    public WorkerResultPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(WorkerManagerCrackResponse response) {
        rabbitTemplate.convertAndSend(
                RabbitTopology.RESULT_EXCHANGE,
                RabbitTopology.RESULT_ROUTING_KEY,
                response
        );
    }
}
