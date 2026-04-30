package ru.ris.crackhash.worker.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.xml.MarshallingHttpMessageConverter;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.web.client.RestTemplate;
import ru.ris.crackhash.contract.xml.ManagerWorkerCrackRequest;
import ru.ris.crackhash.contract.xml.WorkerCancelRequest;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;

import java.util.List;

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

    @Bean
    public RestTemplate managerRestTemplate(Jaxb2Marshaller marshaller) {
        CloseableHttpClient httpClient = HttpClients.custom().build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate restTemplate = new RestTemplate(factory);

        MarshallingHttpMessageConverter xmlConverter = new MarshallingHttpMessageConverter(marshaller);
        xmlConverter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));
        restTemplate.getMessageConverters().add(0, xmlConverter);

        return restTemplate;
    }
}
