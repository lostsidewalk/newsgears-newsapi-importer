package com.lostsidewalk.buffy.newsapi;

import com.kwabenaberko.newsapilib.models.Article;
import com.kwabenaberko.newsapilib.models.Source;
import com.kwabenaberko.newsapilib.models.response.ArticleResponse;
import com.lostsidewalk.buffy.query.QueryDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.Collections.singletonList;

@Slf4j
@Component
class NewsApiMockDataGenerator {

    ArticleResponse buildMockResponse(QueryDefinition q) {
        Long feedId = q.getFeedId();
        ArticleResponse mockResponse = new ArticleResponse();
        mockResponse.setTotalResults(1);
        mockResponse.setStatus("test-status-" + feedId);
        mockResponse.setArticles(buildMockArticle(q));

        return mockResponse;
    }

    private List<Article> buildMockArticle(QueryDefinition q) {
        Long feedId = q.getFeedId();
        Article mockArticle = new Article();
        mockArticle.setAuthor("test-author" + feedId);
        mockArticle.setContent("test-content" + feedId);
        mockArticle.setDescription("test-description" + feedId);
        mockArticle.setSource(buildMockSource(q));
        mockArticle.setPublishedAt("test-published-atr" + feedId);
        mockArticle.setUrl("test-url" + feedId);
        mockArticle.setUrlToImage("test-url-to-image" + feedId);
        mockArticle.setTitle("test-title" + feedId);

        return singletonList(mockArticle);
    }

    private Source buildMockSource(QueryDefinition q) {
        Long feedId = q.getFeedId();
        Source mockSource = new Source();
        mockSource.setCategory("test-source-category-" + feedId);
        mockSource.setDescription("test-source-description-" + feedId);
        mockSource.setCountry("test-source-country-" + feedId);
        mockSource.setUrl("test-source-url-" + feedId);
        mockSource.setId("test-source-id-" + feedId);
        mockSource.setLanguage("test-source-language-" + feedId);
        mockSource.setName("test-source-name-" + feedId);

        return mockSource;
    }
}
