package com.lostsidewalk.buffy.newsapi;

import java.util.*;

public enum NewsApiLanguages {

    AR("ar"),
    DE("de"),
    EN("en"),
    ES("es"),
    FR("fr"),
    HE("he"),
    IT("it"),
    NL("nl"),
    NO("no"),
    PT("pt"),
    RU("ru"),
    SV("sv"),
//    UD("ud"),
    ZH("zh");

    public final String code;

    NewsApiLanguages(String code) {
        this.code = code;
    }

    public static final Map<String, NewsApiLanguages> byCode = new HashMap<>();
    static {
        for (NewsApiLanguages l : values()) {
            byCode.put(l.code, l);
        }
    }

    @SuppressWarnings("unused")
    public static Set<String> codes() {
        return byCode.keySet();
    }

    @SuppressWarnings("unused")
    public static NewsApiLanguages byCode(String code) {
        return byCode.get(code);
    }
}
