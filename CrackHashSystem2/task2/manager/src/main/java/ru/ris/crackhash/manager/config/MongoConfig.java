package ru.ris.crackhash.manager.config;

import com.mongodb.WriteConcern;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer majorityWriteConcernCustomizer() {
        return builder -> builder.writeConcern(WriteConcern.MAJORITY.withJournal(true));
    }
}
