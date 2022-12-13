package com.lostsidewalk.buffy.newsapi;


import java.util.ArrayList;
import java.util.List;

public enum NewsApiCountries {

    AE("ae"),
    AR("ar"),
    AT("at"),
    AU("au"),
    BE("be"),
    BG("bg"),
    BR("br"),
    CA("ca"),
    CH("ch"),
    CN("cn"),
    CO("co"),
    CU("cu"),
    CZ("cz"),
    DE("de"),
    EG("eg"),
    FR("fr"),
    GB("gb"),
    GR("gr"),
    HK("hk"),
    HU("hu"),
    ID("id"),
    IE("ie"),
    IL("il"),
    IN("in"),
    IT("it"),
    JP("jp"),
    KR("kr"),
    LT("lt"),
    LV("lv"),
    MA("ma"),
    MX("mx"),
    MY("my"),
    NG("ng"),
    NL("nl"),
    NO("no"),
    NZ("nz"),
    PH("ph"),
    PL("pl"),
    PT("pt"),
    RO("ro"),
    RS("rs"),
    RU("ru"),
    SA("sa"),
    SE("se"),
    SG("sg"),
    SI("si"),
    SK("sk"),
    TH("th"),
    TR("tr"),
    TW("tw"),
    UA("ua"),
    US("us"),
    VE("ve"),
    ZA("za");

    public final String code;

    NewsApiCountries(String code) {
        this.code = code;
    }

    public static final List<String> allCodes = new ArrayList<>();
    static {
        for (NewsApiCountries l : values()) {
            allCodes.add(l.code);
        }
    }

    @SuppressWarnings("unused")
    public static List<String> codes() {
        return allCodes;
    }
}
