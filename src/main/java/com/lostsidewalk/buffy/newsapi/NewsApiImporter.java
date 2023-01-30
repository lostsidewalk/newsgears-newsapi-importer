package com.lostsidewalk.buffy.newsapi;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import com.lostsidewalk.buffy.post.ContentObject;
import com.lostsidewalk.buffy.post.PostPerson;
import com.lostsidewalk.buffy.post.StagingPost;
import com.lostsidewalk.buffy.query.QueryDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.CollectionUtils.size;
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

    private ExecutorService newsApiV2ThreadPool;

    @PostConstruct
    public void postConstruct() {
        //
        // banner message
        //
        log.info("NewsAPI V2 importer constructed at {}", Instant.now());
        //
        // thread pool setup
        //
        int processorCt = Runtime.getRuntime().availableProcessors() - 1;
        log.info("Starting discovery thread pool: processCount={}", processorCt);
        this.newsApiV2ThreadPool = newFixedThreadPool(processorCt, new ThreadFactoryBuilder().setNameFormat("newsapiv2-importer-%d").build());
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

    //
    //
    //

    @Override
    public void doImport(List<QueryDefinition> queryDefinitions) {
        if (this.configProps.getDisabled()) {
            log.warn("NewsAPI v2 importer is administratively disabled");
            if (this.configProps.getImportMockData()) {
                log.warn("NewsAPI v2 importer importing mock records");
                queryDefinitions.forEach(q ->
                        getArticlesResponseHandler(q.getFeedId(), q.getQueryText(), q.getQueryType(), q.getUsername())
                                .onSuccess(newsApiMockDataGenerator.buildMockResponse(q)));
            }
            return;
        }

        log.info("NewsAPI V2 importer running at {}", Instant.now());

        List<QueryDefinition> supportedQueryDefinitions = queryDefinitions.parallelStream()
                .filter(q -> supportsQueryType(q.getQueryType()))
                .toList();

        CountDownLatch latch = new CountDownLatch(size(supportedQueryDefinitions));
        log.info("NewsAPI V2 import latch initialized to: {}", latch.getCount());
        supportedQueryDefinitions.forEach(q -> newsApiV2ThreadPool.submit(() -> {
            this.performImport(q, getArticlesResponseHandler(
                    q.getFeedId(),
                    String.format("[query: %s]", q.getQueryText()), q.getQueryType(), q.getUsername())
            );
            latch.countDown();
            if (latch.getCount() % 50 == 0) {
                log.info("NewsApi V2 import latch currently at {}: ", latch.getCount());
            }
        }));

        log.info("NewsAPI V2 importer finished at {}", Instant.now());
    }

    private NewsApiClient.ArticlesResponseCallback getArticlesResponseHandler(Long feedId, String query, String queryType, String username) {
        return new NewsApiClient.ArticlesResponseCallback() {
            @Override
            public void onSuccess(ArticleResponse response) {
                try {
                    AtomicInteger importCt = new AtomicInteger(0);
                    importArticleResponse(feedId, query, response, username).forEach(s -> {
                        log.debug("Adding post hash={} to queue for feedId={}, username={}", s.getPostHash(), feedId, username);
                        successAggregator.offer(s);
                        importCt.getAndIncrement();
                    });
                    log.info("Import success, feedId={}, username={}, queryType={}, queryText={}, importCt={}", feedId, username, queryType, query, importCt.intValue());
                } catch (Exception e) {
                    log.error("Import failure, feedId={}, username={}, queryType={}, queryText={} due to: {}", feedId, username, queryType, query, e.getMessage());
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                errorAggregator.offer(throwable);
            }
        };
    }

    //
    //
    //

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
                    Set<StagingPost> stagingPosts = importArticleResponse(queryDefinition.getFeedId(), queryDefinition.getQueryText(), response, queryDefinition.getUsername());
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

    private static final Type NEWSAPI_SOURCES_TYPE = new TypeToken<List<NewsApiSources>>() {}.getType();

    // import according to params defined by newsApiImportConfig, and build staging posts tagged w/feedIdent
    private void performImport(QueryDefinition queryDefinition, NewsApiClient.ArticlesResponseCallback articleResponseHandler) {
        String username = queryDefinition.getUsername();
        Long feedId = queryDefinition.getFeedId();
        log.info("Importing feedId={}, username={}, queryDefinition={}", feedId, username, queryDefinition);
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
            sources = GSON.fromJson(sourcesArr, NEWSAPI_SOURCES_TYPE);
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

    private static Set<StagingPost> importArticleResponse(Long feedId, String query, ArticleResponse articleResponse, String username) throws NoSuchAlgorithmException {
        Set<StagingPost> stagingPosts = new HashSet<>();
        MessageDigest md = MessageDigest.getInstance("MD5");
        for (Article a : articleResponse.getArticles()) {
            // generate source object
            Serializable objectSrc = getObjectSrc(a);
            // generate contents
            List<ContentObject> articleContents = null;
            String contentStr = a.getContent();
            if (isNotBlank(contentStr)) {
                articleContents = singletonList(ContentObject.from("text", contentStr));
            }
            // generate staging post
            StagingPost p = StagingPost.from(
                    NEWS_API_V2_IMPORTER_ID, // importer Id
                    feedId, // feed Id
                    getImporterDesc(query), // importer desc
                    objectSrc, // source
                    ofNullable(a.getSource()).map(Source::getName).orElse(null), // source name
                    ofNullable(a.getSource()).map(Source::getUrl).orElse(null), // source name
                    ContentObject.from("text", a.getTitle()), // post title
                    ContentObject.from("text", a.getDescription()), // post description
                    articleContents, // post_contents
                    null, // post_media
                    null, // post_itunes
                    a.getUrl(), // post url
                    null, // post urls
                    a.getUrlToImage(), // post img url
                    // no img transport ident
                    new Date(), // import timestamp
                    computeHash(md, feedId, objectSrc), // post hash
                    username, // post username
                    null, // post comment
                    null, // post rights
                    null, // contributors
                    getAuthors(a), // authors
                    getPostCategories(a), // post categories
                    toTimestamp(a.getPublishedAt()), // publish timestamp
                    null, // expiration timestamp
                    null, // enclosures
                    null // last updated timestamp
            );
            // accumulate staging posts
            stagingPosts.add(p);
        }

        return stagingPosts;
    }

    private static String getImporterDesc(String query) {
        return trimToEmpty(query);
    }

    private static Serializable getObjectSrc(Article article) {
        JsonElement objectSrc = GSON.toJsonTree(article);
        if (objectSrc.isJsonObject()) {
            return objectSrc.toString();
        }

        return null;
    }

    private static List<String> getPostCategories(Article article) {
        return ofNullable(article.getSource()).map(Source::getCategory).stream().collect(toList());
    }

    private static List<PostPerson> getAuthors(Article article) {
        return ofNullable(article.getAuthor()).map(author -> {
            PostPerson p = new PostPerson();
            p.setName(author);
            return p;
        }).stream().collect(toList());
    }

    private static Date toTimestamp(String str) {
        DateTimeFormatter f = ISO_INSTANT.withZone(ZoneId.systemDefault());
        ZonedDateTime zdt = ZonedDateTime.parse(str, f);
        return Date.from(zdt.toInstant());
    }

    private static final Gson GSON = new Gson();

    private static String computeHash(MessageDigest md, Long feedId, Serializable objectSrc) {
        return printHexBinary(md.digest(serialize(String.format("%s:%s", feedId, objectSrc))));
    }
}
