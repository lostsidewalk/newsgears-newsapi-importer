package com.lostsidewalk.buffy.newsapi;

import java.util.ArrayList;
import java.util.List;

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

    public static final List<String> allnames = new ArrayList<>();
    static {
        for (NewsApiCategories l : values()) {
            allnames.add(l.name);
        }
    }

    @SuppressWarnings("unused")
    public static List<String> names() {
        return allnames;
    }
}
