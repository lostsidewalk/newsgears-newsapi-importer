package com.lostsidewalk.buffy.newsapi;

import java.util.*;

public enum NewsApiCategories {

    BUSINESS("business"),
    ENTERTAINMENT("entertainment"),
    GENERAL("general"),
    HEALTH("health"),
    SCIENCE("science"),
    SPORTS("sports"),
    TECHNOLOGY("technology");

    public final String name;

    NewsApiCategories(String name) {
        this.name = name;
    }

    public static final Map<String, NewsApiCategories> byName = new HashMap<>();
    static {
        for (NewsApiCategories l : values()) {
            byName.put(l.name, l);
        }
    }

    @SuppressWarnings("unused")
    public static Set<String> names() {
        return byName.keySet();
    }

    @SuppressWarnings("unused")
    public static NewsApiCategories byName(String name) {
        return byName.get(name);
    }
}
