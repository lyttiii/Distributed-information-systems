package ru.ris.crackhash.worker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import ru.ris.crackhash.contract.xml.ManagerWorkerCrackRequest;
import ru.ris.crackhash.contract.xml.WorkerCancelRequest;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;

@Configuration
public class RestClientConfig {

    @Bean
    public Jaxb2Marshaller jaxb2Marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(
                ManagerWorkerCrackRequest.class,
                WorkerManagerCrackResponse.class,
                WorkerCancelRequest.class
        );
        return marshaller;
    }
}
