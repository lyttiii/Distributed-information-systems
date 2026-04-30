package ru.ris.crackhash.worker.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MarshallingMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

@Configuration
public class RabbitConfig {

    @Bean
    public Declarables topology(WorkerProperties workerProperties) {
        DirectExchange taskExchange = new DirectExchange(RabbitTopology.TASK_EXCHANGE, true, false);
        Queue taskQueue = new Queue(RabbitTopology.TASK_QUEUE, true);
        Binding taskBinding = BindingBuilder.bind(taskQueue).to(taskExchange).with(RabbitTopology.TASK_ROUTING_KEY);

        DirectExchange resultExchange = new DirectExchange(RabbitTopology.RESULT_EXCHANGE, true, false);
        DirectExchange cancelExchange = new DirectExchange(RabbitTopology.CANCEL_EXCHANGE, true, false);
        Queue cancelQueue = new Queue(cancelQueueName(workerProperties), true);
        Binding cancelBinding = BindingBuilder.bind(cancelQueue)
                .to(cancelExchange)
                .with(RabbitTopology.CANCEL_ROUTING_KEY_PREFIX + workerProperties.getWorkerId());

        return new Declarables(
                taskExchange,
                taskQueue,
                taskBinding,
                resultExchange,
                cancelExchange,
                cancelQueue,
                cancelBinding
        );
    }

    @Bean
    public MessageConverter rabbitXmlMessageConverter(Jaxb2Marshaller marshaller) {
        return new MarshallingMessageConverter(marshaller, marshaller);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitXmlMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitXmlMessageConverter);
        template.setBeforePublishPostProcessors(message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory workerRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitXmlMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitXmlMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        return factory;
    }

    @Bean(name = "workerCancelQueueName")
    public String workerCancelQueueName(WorkerProperties workerProperties) {
        return cancelQueueName(workerProperties);
    }

    public static String cancelQueueName(WorkerProperties workerProperties) {
        return RabbitTopology.CANCEL_QUEUE_PREFIX + workerProperties.getWorkerId();
    }
}
