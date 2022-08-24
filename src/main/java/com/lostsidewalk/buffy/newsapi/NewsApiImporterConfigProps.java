package com.lostsidewalk.buffy.newsapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;


@Configuration
@ConfigurationProperties(prefix = "news.api")
public class NewsApiImporterConfigProps {

    private String key;

    private String sourceCountry;

    private boolean debugSources;

    private long importTimeoutMs;

    private Map<String, String> tag;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getSourceCountry() {
        return sourceCountry;
    }

    public void setSourceCountry(String sourceCountry) {
        this.sourceCountry = sourceCountry;
    }

    public boolean getDebugSources() {
        return debugSources;
    }

    public void setDebugSources(boolean debugSources) {
        this.debugSources = debugSources;
    }

    public long getImportTimeoutMs() {
        return importTimeoutMs;
    }

    public void setImportTimeoutMs(long importTimeoutMs) {
        this.importTimeoutMs = importTimeoutMs;
    }

    public Map<String, String> getTag() {
        return tag;
    }

    public void setTag(Map<String, String> tag) {
        this.tag = tag;
    }
}
