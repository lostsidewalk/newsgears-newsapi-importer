package com.lostsidewalk.buffy.newsapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "news.api")
public class NewsApiImporterConfigProps {

    private boolean disabled;

    private String key;

    private boolean debugSources;

    private boolean importMockData;

    public boolean getDisabled() {
        return disabled;
    }

    @SuppressWarnings("unused")
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public String getKey() {
        return key;
    }

    @SuppressWarnings("unused")
    public void setKey(String key) {
        this.key = key;
    }

    public boolean getDebugSources() {
        return debugSources;
    }

    @SuppressWarnings("unused")
    public void setDebugSources(boolean debugSources) {
        this.debugSources = debugSources;
    }

    public boolean getImportMockData() {
        return importMockData;
    }

    @SuppressWarnings("unused")
    public void setImportMockData(boolean importMockData) {
        this.importMockData = importMockData;
    }
}
