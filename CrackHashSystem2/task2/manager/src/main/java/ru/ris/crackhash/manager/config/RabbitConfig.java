package ru.ris.crackhash.manager.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.AcknowledgeMode;
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
    public DirectExchange taskExchange() {
        return new DirectExchange(RabbitTopology.TASK_EXCHANGE, true, false);
    }

    @Bean
    public Queue taskQueue() {
        return new Queue(RabbitTopology.TASK_QUEUE, true);
    }

    @Bean
    public Binding taskBinding() {
        return BindingBuilder.bind(taskQueue()).to(taskExchange()).with(RabbitTopology.TASK_ROUTING_KEY);
    }

    @Bean
    public DirectExchange resultExchange() {
        return new DirectExchange(RabbitTopology.RESULT_EXCHANGE, true, false);
    }

    @Bean
    public Queue resultQueue() {
        return new Queue(RabbitTopology.RESULT_QUEUE, true);
    }

    @Bean
    public Binding resultBinding() {
        return BindingBuilder.bind(resultQueue()).to(resultExchange()).with(RabbitTopology.RESULT_ROUTING_KEY);
    }

    @Bean
    public DirectExchange cancelExchange() {
        return new DirectExchange(RabbitTopology.CANCEL_EXCHANGE, true, false);
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
    public SimpleRabbitListenerContainerFactory managerRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitXmlMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitXmlMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
