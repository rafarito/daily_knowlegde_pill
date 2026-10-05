package dev.rafael.dailyknowledge;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UrlNormalizerTest {

    @Test
    void normalizesCommonVariations() {
        String expected = "https://github.com/jgm/pandoc";
        assertEquals(expected, UrlNormalizer.normalize("https://github.com/jgm/pandoc"));
        assertEquals(expected, UrlNormalizer.normalize("https://github.com/jgm/pandoc/"));
        assertEquals(expected, UrlNormalizer.normalize("https://github.com/jgm/pandoc.git"));
        assertEquals(expected, UrlNormalizer.normalize("http://www.GitHub.com/JGM/Pandoc.git/"));
        assertEquals(expected, UrlNormalizer.normalize("github.com/jgm/pandoc?tab=readme#install"));
        assertEquals(expected, UrlNormalizer.normalize("  https://github.com/jgm/pandoc  "));
    }

    @Test
    void extractsGithubRepo() {
        assertEquals(Optional.of("jgm/pandoc"), UrlNormalizer.githubRepo("https://github.com/jgm/pandoc/tree/main/src"));
        assertEquals(Optional.of("cjpais/handy"), UrlNormalizer.githubRepo("https://www.github.com/cjpais/Handy.git"));
        assertEquals(Optional.empty(), UrlNormalizer.githubRepo("https://github.com/jgm"));
        assertEquals(Optional.empty(), UrlNormalizer.githubRepo("https://pandoc.org"));
        assertEquals(Optional.empty(), UrlNormalizer.githubRepo("https://gitlab.com/a/b"));
    }

    @Test
    void shortHost() {
        assertEquals("pandoc.org", UrlNormalizer.host("https://www.pandoc.org/installing.html"));
        assertEquals("github.com", UrlNormalizer.host("https://github.com/jgm/pandoc"));
    }
}
