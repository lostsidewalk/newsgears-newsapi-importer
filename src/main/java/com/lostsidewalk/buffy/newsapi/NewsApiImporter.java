package com.lostsidewalk.buffy.newsapi;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.kwabenaberko.newsapilib.NewsApiClient;
import com.kwabenaberko.newsapilib.models.Article;
import com.kwabenaberko.newsapilib.models.Source;
import com.kwabenaberko.newsapilib.models.request.EverythingRequest;
import com.kwabenaberko.newsapilib.models.request.SourcesRequest;
import com.kwabenaberko.newsapilib.models.request.TopHeadlinesRequest;
import com.kwabenaberko.newsapilib.models.response.ArticleResponse;
import com.kwabenaberko.newsapilib.models.response.SourcesResponse;
import com.lostsidewalk.buffy.Importer;
import com.lostsidewalk.buffy.post.StagingPost;
import com.lostsidewalk.buffy.query.QueryDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.SerializationUtils.serialize;
import static org.apache.commons.lang3.StringUtils.*;

@Slf4j
@Component
public class NewsApiImporter implements Importer {

    @Autowired
    NewsApiImporterConfigProps configProps;

    @Autowired
    Queue<StagingPost> successAggregator;

    @Autowired
    Queue<Throwable> errorAggregator;

    @Autowired
    NewsApiMockDataGenerator newsApiMockDataGenerator;

    @Autowired
    NewsApiClient newsApiClient;

    @PostConstruct
    public void postConstruct() {
        //
        // banner message
        //
        log.info("NewsAPI V2 importer constructed at {}", Instant.now());
        //
        // dump source data from /v2/top-headlines/sources on start-up (if debugSources eq true)
        //
        if (isTrue(this.configProps.getDebugSources())) {
            log.info("Getting sources...");
            newsApiClient.getSources(
                    new SourcesRequest.Builder().build(),
                    new NewsApiClient.SourcesCallback() {
                        @Override
                        public void onSuccess(SourcesResponse response) {
                            for (Source source : response.getSources()) {
                                log.info("source=[name={}, description={}, url={}, category={}, country={}, language={})]",
                                        source.getName(), source.getDescription(), source.getUrl(), source.getCategory(), source.getCountry(), source.getLanguage());
                            }
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            log.error(throwable.getMessage());
                        }
                    }
            );
        }
    }

    @Override
    public void doImport(List<QueryDefinition> queryDefinitions) {
        if (this.configProps.getDisabled()) {
            log.warn("NewsAPI v2 importer is administratively disabled");
            if (this.configProps.getImportMockData()) {
                log.warn("NewsAPI v2 importer importing mock records");
                queryDefinitions.forEach(q ->
                        getArticlesResponseHandler(q.getFeedIdent(), q.getQueryText(), q.getQueryType(), q.getUsername())
                                .onSuccess(newsApiMockDataGenerator.buildMockResponse(q)));
            }
            return;
        }

        log.info("NewsAPI V2 importer running at {}", Instant.now());
        queryDefinitions.stream()
                .filter(q -> supportsQueryType(q.getQueryType()))
                .forEach(q ->
                        this.performImport(q, getArticlesResponseHandler(q.getFeedIdent(), String.format("[query: %s]", q.getQueryText()), q.getQueryType(), q.getUsername()))
                );

        log.info("NewsAPI V2 importer finished at {}", Instant.now());
    }

    private NewsApiClient.ArticlesResponseCallback getArticlesResponseHandler(String feedIdent, String query, String queryType, String username) {
        return new NewsApiClient.ArticlesResponseCallback() {
            @Override
            public void onSuccess(ArticleResponse response) {
                try {
                    AtomicInteger importCt = new AtomicInteger(0);
                    importArticleResponse(feedIdent, query, response, username).forEach(s -> {
                        log.debug("Adding post hash={} to queue for feedIdent={}, username={}", s.getPostHash(), feedIdent, username);
                        successAggregator.offer(s);
                        importCt.getAndIncrement();
                    });
                    log.info("Import success, feedIdent={}, username={}, queryType={}, queryText={}, importCt={}", feedIdent, username, queryType, query, importCt.intValue());
                } catch (Exception e) {
                    log.error("Import failure, feedIdent={}, username={}, queryType={}, queryText={} due to: {}", feedIdent, username, queryType, query, e.getMessage());
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                errorAggregator.offer(throwable);
            }
        };
    }

    private boolean supportsQueryType(String queryType) {
        return equalsAnyIgnoreCase(queryType, SUPPORTED_QUERY_TYPES);
    }

    public static final String NEWSAPIV2_EVERYTHING = "NEWSAPIV2_EVERYTHING";

    public static final String NEWSAPIV2_HEADLINES = "NEWSAPIV2_HEADLINES";

    private static final String[] SUPPORTED_QUERY_TYPES = new String[] {
            NEWSAPIV2_EVERYTHING, NEWSAPIV2_HEADLINES
    };

    @Override
    public ImporterMetrics performImport(QueryDefinition queryDefinition, ImportResponseCallback importResponseCallback) {

        class CountingArticlesResponseCallback implements NewsApiClient.ArticlesResponseCallback {

            private int successCt, errorCt;

            @Override
            public void onSuccess(ArticleResponse response) {
                try {
                    Set<StagingPost> stagingPosts = importArticleResponse(queryDefinition.getFeedIdent(), queryDefinition.getQueryText(), response, queryDefinition.getUsername());
                    importResponseCallback.onSuccess(stagingPosts);
                    successCt++;
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                importResponseCallback.onFailure(throwable);
                errorCt++;
            }
        }

        CountingArticlesResponseCallback callback = new CountingArticlesResponseCallback();
        this.performImport(queryDefinition, callback);
        return new ImporterMetrics(callback.successCt, callback.errorCt);
    }

    private static String getStringProperty(JsonObject obj, String propName) {
        return obj != null && obj.has(propName) ? obj.get(propName).getAsString() : null;
    }

    private static JsonArray getArrayProperty(JsonObject obj, @SuppressWarnings("SameParameterValue") String propName) {
        return obj != null && obj.has(propName) ? obj.get(propName).getAsJsonArray() : null;
    }

    // import according to params defined by newsApiImportConfig, and build staging posts tagged w/feedIdent
    private void performImport(QueryDefinition queryDefinition, NewsApiClient.ArticlesResponseCallback articleResponseHandler) {
        String username = queryDefinition.getUsername();
        String feedIdent = queryDefinition.getFeedIdent();
        log.info("Importing feedIdent={}, username={}, queryDefinition={}", feedIdent, username, queryDefinition);
        // query expression
        String queryText = queryDefinition.getQueryText();
        // query sources
        JsonObject queryConfigObj = ofNullable(queryDefinition.getQueryConfig())
                .map(Object::toString)
                .map(s -> GSON.fromJson(s, JsonObject.class))
                .orElse(null);
        List<NewsApiSources> sources = null;
        JsonArray sourcesArr = getArrayProperty(queryConfigObj, "sources");
        if (sourcesArr != null) {
            sources = GSON.fromJson(sourcesArr, new TypeToken<List<NewsApiSources>>() {}.getType());
        }
        // query language (ar de en es fr he it nl no pt ru sv ud zh)
        String queryLanguage = getStringProperty(queryConfigObj, "language");

        switch (queryDefinition.getQueryType()) {
            case NEWSAPIV2_EVERYTHING -> {
                EverythingRequest.Builder builder = new EverythingRequest.Builder();
                if (isNotBlank(queryText)) {
                    builder.q(queryText);
                }
                if (isNotBlank(queryLanguage)) {
                    builder.language(NewsApiLanguages.valueOf(queryLanguage).code);
                }
                if (isNotEmpty(sources)) {
                    builder.sources(sources.stream().map(s -> s.name).collect(joining(",")));
                }
                newsApiClient.getEverything(builder.build(), articleResponseHandler);
            }
            case NEWSAPIV2_HEADLINES -> {
                TopHeadlinesRequest.Builder builder = new TopHeadlinesRequest.Builder();
                if (isNotBlank(queryText)) {
                    builder.q(queryText);
                }
                if (isNotBlank(queryLanguage)) {
                    builder.language(NewsApiLanguages.valueOf(queryLanguage).code);
                }
                if (isNotEmpty(sources)) {
                    builder.sources(sources.stream().map(s -> s.name).collect(joining(",")));
                } else {
                    // query country
                    String queryCountry = getStringProperty(queryConfigObj, "country");
                    if (isNotBlank(queryCountry)) {
                        builder.country(NewsApiCountries.valueOf(queryCountry).code);
                    }
                    // query category (business, entertainment, general, health, science, sports, technology)
                    String queryCategory = getStringProperty(queryConfigObj, "category");
                    if (isNotBlank(queryCategory)) {
                        builder.category(NewsApiCategories.valueOf(queryCategory).name);
                    }
                }
                this.newsApiClient.getTopHeadlines(builder.build(), articleResponseHandler);
            }
            default -> log.error("Query type not supported by this importer: queryType={}, importerId={}", queryDefinition.getQueryType(), getImporterId());
        }
    }

    @Override
    public String getImporterId() {
        return NEWS_API_V2_IMPORTER_ID;
    }

    private static final String NEWS_API_V2_IMPORTER_ID = "NewsApiV2";

    private static Set<StagingPost> importArticleResponse(String feedIdent, String query, ArticleResponse articleResponse, String username) throws NoSuchAlgorithmException {
        Set<StagingPost> stagingPosts = new HashSet<>();
        MessageDigest md = MessageDigest.getInstance("MD5");
        for (Article a : articleResponse.getArticles()) {
            JsonElement objectSrc = GSON.toJsonTree(a);
            if (!objectSrc.isJsonObject()) {
                continue;
            }
            StagingPost p = StagingPost.from(
                    NEWS_API_V2_IMPORTER_ID, // importer Id
                    feedIdent, // feed ident
                    getImporterDesc(query), // importer desc
                    objectSrc.toString(), // source
                    ofNullable(a.getSource()).map(Source::getName).orElse(EMPTY), // source name
                    ofNullable(a.getSource()).map(Source::getUrl).orElse(EMPTY), // source name
                    a.getTitle(), // post title
                    a.getDescription(), // post description
                    a.getUrl(), // post url
                    a.getUrlToImage(), // post img url
                    // no img transport ident
                    new Date(), // import timestamp
                    computeHash(md, feedIdent, objectSrc), // post hash
                    username, // post username
                    null, // post comment
                    false, // is published
                    null, // post rights
                    null, // xml base
                    null, // contributor name
                    null, // contributor email
                    a.getAuthor(), // author name
                    null, // author email
                    ofNullable(a.getSource()).map(Source::getCategory).orElse(null), // post category
                    toTimestamp(a.getPublishedAt()), // publish timestamp
                    null, // expiration timestamp
                    null, // enclosure url
                    null // last updated timestamp
            );
            stagingPosts.add(p);
        }

        return stagingPosts;
    }

    private static String getImporterDesc(String query) {
        return String.format("[query=%s]", query);
    }

    private static Date toTimestamp(String str) {
        DateTimeFormatter f = ISO_INSTANT.withZone(ZoneId.systemDefault());
        ZonedDateTime zdt = ZonedDateTime.parse(str, f);
        return Date.from(zdt.toInstant());
    }

    private static final Gson GSON = new Gson();

    private static String computeHash(MessageDigest md, String feedIdent, JsonElement objectSrc) {
        return printHexBinary(md.digest(serialize(String.format("%s:%s", feedIdent, objectSrc.toString()))));
    }
}
