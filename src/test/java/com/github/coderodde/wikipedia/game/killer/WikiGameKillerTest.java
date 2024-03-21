package com.github.coderodde.wikipedia.game.killer;


import com.github.coderodde.wikipedia.game.killer.WikiGameKiller.CommandLineException;
import static org.junit.Assert.fail;
import org.junit.Test;

public final class WikiGameKillerTest {
    
    @Test
    public void checkWikipediaFormatValidNoProtocol() {
        try {
            WikiGameKiller.checkWikipediaArticleFormat(
                    "en.wikipedia.org/wiki/hello");
        } catch (Exception ex) {
            fail();
        }
    }
    
    @Test
    public void checkWikipediaFormatValidHttpProtocol() {
        try {
            WikiGameKiller.checkWikipediaArticleFormat(
                    "http://en.wikipedia.org/wiki/hello");
        } catch (Exception ex) {
            fail();
        }
    }
    
    @Test
    public void checkWikipediaFormatValidHttpsProtocol() {
        try {
            WikiGameKiller.checkWikipediaArticleFormat(
                    "https://en.wikipedia.org/wiki/hello");
        } catch (Exception ex) {
            fail();
        }
    }
    
    @Test(expected = CommandLineException.class)
    public void throwOnBadProtocol() {
        WikiGameKiller.checkWikipediaArticleFormat(
                "htp://en.wikipedia.org/wiki/hello");
    }
    
    @Test(expected = CommandLineException.class)
    public void throwOnBadLanguageCode() {
        WikiGameKiller.checkWikipediaArticleFormat(
                "damn.wikipedia.org/wiki/hello");
    }
    
    @Test(expected = CommandLineException.class)
    public void throwOnBadHostName() {
        WikiGameKiller.checkWikipediaArticleFormat(
                "fi.wikipdia.org/wiki/hello");
    }
    
    @Test(expected = CommandLineException.class)
    public void throwOnMissingArticleTitle() {
        WikiGameKiller.checkWikipediaArticleFormat(
                "fi.wikipdia.org/wiki/");
    }
}
