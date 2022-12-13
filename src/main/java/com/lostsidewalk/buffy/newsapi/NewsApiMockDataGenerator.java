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
        String feedIdent = q.getFeedIdent();
        ArticleResponse mockResponse = new ArticleResponse();
        mockResponse.setTotalResults(1);
        mockResponse.setStatus("test-status-" + feedIdent);
        mockResponse.setArticles(buildMockArticle(q));

        return mockResponse;
    }

    private List<Article> buildMockArticle(QueryDefinition q) {
        String feedIdent = q.getFeedIdent();
        Article mockArticle = new Article();
        mockArticle.setAuthor("test-author" + feedIdent);
        mockArticle.setContent("test-content" + feedIdent);
        mockArticle.setDescription("test-description" + feedIdent);
        mockArticle.setSource(buildMockSource(q));
        mockArticle.setPublishedAt("test-published-atr" + feedIdent);
        mockArticle.setUrl("test-url" + feedIdent);
        mockArticle.setUrlToImage("test-url-to-image" + feedIdent);
        mockArticle.setTitle("test-title" + feedIdent);

        return singletonList(mockArticle);
    }

    private Source buildMockSource(QueryDefinition q) {
        String feedIdent = q.getFeedIdent();
        Source mockSource = new Source();
        mockSource.setCategory("test-source-category-" + feedIdent);
        mockSource.setDescription("test-source-description-" + feedIdent);
        mockSource.setCountry("test-source-country-" + feedIdent);
        mockSource.setUrl("test-source-url-" + feedIdent);
        mockSource.setId("test-source-id-" + feedIdent);
        mockSource.setLanguage("test-source-language-" + feedIdent);
        mockSource.setName("test-source-name-" + feedIdent);

        return mockSource;
    }
}
