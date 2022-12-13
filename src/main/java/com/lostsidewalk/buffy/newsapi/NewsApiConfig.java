package com.lostsidewalk.buffy.newsapi;

import com.kwabenaberko.newsapilib.NewsApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NewsApiConfig {

    @Autowired
    NewsApiImporterConfigProps configProps;

    @Bean
    NewsApiClient newsApiClient() {
        return new NewsApiClient(configProps.getKey());
    }
}
