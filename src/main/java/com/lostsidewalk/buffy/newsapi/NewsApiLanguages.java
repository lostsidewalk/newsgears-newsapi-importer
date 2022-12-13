package com.lostsidewalk.buffy.newsapi;

import java.util.ArrayList;
import java.util.List;

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

    public static final List<String> allCodes = new ArrayList<>();
    static {
        for (NewsApiLanguages l : values()) {
            allCodes.add(l.code);
        }
    }

    @SuppressWarnings("unused")
    public static List<String> codes() {
        return allCodes;
    }
}
