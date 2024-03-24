package com.github.coderodde.wikipedia.game.killer;

import org.junit.Test;

public final class WikiGameKillerTest {
    
    @Test
    public void checkWikipediaFormatValidNoProtocol() {
        // Should not throw:
        WikiGameKiller.checkWikipediaArticleFormat(
                "en.wikipedia.org/wiki/hello");
    }
    
    @Test
    public void checkWikipediaFormatValidHttpProtocol() {
        // Should not throw:
        WikiGameKiller.checkWikipediaArticleFormat(
                "http://en.wikipedia.org/wiki/hello");
    }
    
    @Test
    public void checkWikipediaFormatValidHttpsProtocol() {
        // Should not throw:
        WikiGameKiller.checkWikipediaArticleFormat(
                "https://en.wikipedia.org/wiki/hello");
    }
    
    @Test(expected = RuntimeException.class)
    public void throwOnBadProtocol() {
        WikiGameKiller.checkWikipediaArticleFormat(
                "htp://en.wikipedia.org/wiki/hello");
    }
    
    @Test(expected = RuntimeException.class)
    public void throwOnBadLanguageCode() {
        WikiGameKiller.checkWikipediaArticleFormat(
                "damn.wikipedia.org/wiki/hello");
    }
    
    @Test(expected = RuntimeException.class)
    public void throwOnBadHostName() {
        WikiGameKiller.checkWikipediaArticleFormat(
                "fi.wikipdia.org/wiki/hello");
    }
    
    @Test(expected = RuntimeException.class)
    public void throwOnMissingArticleTitle() {
        WikiGameKiller.checkWikipediaArticleFormat(
                "fi.wikipedia.org/wiki/");
    }
}
