/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.roller.weblogger.business.translation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link TranslationService}.
 *
 * <p>All tests use a Mockito-mocked {@link TranslationProvider} so no real
 * HTTP calls are made.  The package-private
 * {@code TranslationService(provider, ttl, maxEntries, maxTexts, maxChars)}
 * constructor is used to inject the mock and to control cache / limit
 * parameters directly.</p>
 *
 * <h3>Test categories</h3>
 * <ul>
 *   <li>{@link BasicTranslationTests} – happy-path translation.</li>
 *   <li>{@link CachingTests} – cache hits, misses, TTL expiry, clear.</li>
 *   <li>{@link BatchingTests} – partial cache hits; only misses reach
 *       the provider.</li>
 *   <li>{@link ValidationTests} – null/empty inputs, bad language codes,
 *       over-limit batches.</li>
 *   <li>{@link EdgeCaseTests} – blank items pass-through, char truncation,
 *       provider size mismatch.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranslationServiceTest {

    // Supported languages for the mock provider
    private static final Set<String> SUPPORTED = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("en", "hi", "fr", "es", "de")));

    @Mock
    private TranslationProvider mockProvider;

    /** Helper: creates a TranslationService with the shared mock, given TTL in ms. */
    private TranslationService service(long ttlMs) {
        return new TranslationService(mockProvider, ttlMs, 100, 50, 500);
    }

    @BeforeEach
    void configureMock() {
        // Every test gets a provider that understands supported languages
        when(mockProvider.getProviderName()).thenReturn("mock");
        when(mockProvider.getSupportedLanguages()).thenReturn(SUPPORTED);
    }

    // =========================================================================
    //  Basic translation
    // =========================================================================

    @Nested
    @DisplayName("Basic translation")
    class BasicTranslationTests {

        @Test
        @DisplayName("single text is translated and returned at index 0")
        void singleText() throws TranslationException {
            when(mockProvider.translate(List.of("Hello"), "en", "hi"))
                    .thenReturn(List.of("नमस्ते"));

            TranslationService svc = service(60_000);
            List<String> result = svc.translate(List.of("Hello"), "en", "hi");

            assertEquals(1, result.size());
            assertEquals("नमस्ते", result.get(0));
            verify(mockProvider, times(1)).translate(List.of("Hello"), "en", "hi");
        }

        @Test
        @DisplayName("multiple texts preserve order")
        void multipleTextsPreserveOrder() throws TranslationException {
            List<String> inputs   = List.of("One", "Two", "Three");
            List<String> outputs  = List.of("Un", "Deux", "Trois");

            when(mockProvider.translate(inputs, "en", "fr")).thenReturn(outputs);

            TranslationService svc = service(60_000);
            List<String> result = svc.translate(inputs, "en", "fr");

            assertEquals(outputs, result);
        }

        @Test
        @DisplayName("source='auto' is forwarded to the provider")
        void autoSourceLanguage() throws TranslationException {
            when(mockProvider.translate(List.of("Hola"), "auto", "en"))
                    .thenReturn(List.of("Hello"));

            TranslationService svc = service(60_000);
            List<String> result = svc.translate(List.of("Hola"), "auto", "en");

            assertEquals("Hello", result.get(0));
            verify(mockProvider).translate(List.of("Hola"), "auto", "en");
        }

        @Test
        @DisplayName("empty input list returns empty list without calling provider")
        void emptyInputReturnsEmptyList() throws TranslationException {
            TranslationService svc = service(60_000);
            List<String> result = svc.translate(Collections.emptyList(), "en", "fr");

            assertTrue(result.isEmpty());
            verify(mockProvider, never()).translate(anyList(), anyString(), anyString());
        }

        @Test
        @DisplayName("getProviderName returns the mock provider's name")
        void getProviderName() {
            assertEquals("mock", service(60_000).getProviderName());
        }

        @Test
        @DisplayName("getSupportedLanguages delegates to provider")
        void getSupportedLanguages() {
            assertEquals(SUPPORTED, service(60_000).getSupportedLanguages());
        }
    }

    // =========================================================================
    //  Caching
    // =========================================================================

    @Nested
    @DisplayName("Caching")
    class CachingTests {

        @Test
        @DisplayName("second call with same input uses cache, provider called once")
        void cacheHitSkipsProvider() throws TranslationException {
            when(mockProvider.translate(List.of("Bonjour"), "fr", "en"))
                    .thenReturn(List.of("Hello"));

            TranslationService svc = service(60_000);

            // First call → cache miss → provider invoked
            List<String> r1 = svc.translate(List.of("Bonjour"), "fr", "en");
            // Second call → cache hit → provider NOT invoked again
            List<String> r2 = svc.translate(List.of("Bonjour"), "fr", "en");

            assertEquals("Hello", r1.get(0));
            assertEquals("Hello", r2.get(0));
            // Provider was called exactly once despite two translate() calls
            verify(mockProvider, times(1)).translate(anyList(), anyString(), anyString());
        }

        @Test
        @DisplayName("different target language uses separate cache entry")
        void differentTargetLanguageSeparateEntry() throws TranslationException {
            when(mockProvider.translate(List.of("Hello"), "en", "fr"))
                    .thenReturn(List.of("Bonjour"));
            when(mockProvider.translate(List.of("Hello"), "en", "de"))
                    .thenReturn(List.of("Hallo"));

            TranslationService svc = service(60_000);
            assertEquals("Bonjour", svc.translate(List.of("Hello"), "en", "fr").get(0));
            assertEquals("Hallo",   svc.translate(List.of("Hello"), "en", "de").get(0));

            verify(mockProvider, times(2)).translate(anyList(), anyString(), anyString());
        }

        @Test
        @DisplayName("expired cache entry causes provider to be called again")
        void expiredCacheEntryBypassesCache() throws TranslationException, InterruptedException {
            when(mockProvider.translate(List.of("Hi"), "en", "hi"))
                    .thenReturn(List.of("नमस्ते"));

            // TTL = 50 ms so it expires quickly in tests
            TranslationService svc = service(50);

            svc.translate(List.of("Hi"), "en", "hi");  // miss → stored
            Thread.sleep(100);                          // wait for expiry
            svc.translate(List.of("Hi"), "en", "hi");  // miss again (expired)

            verify(mockProvider, times(2)).translate(anyList(), anyString(), anyString());
        }

        @Test
        @DisplayName("clearCache removes all entries")
        void clearCacheEvictsAll() throws TranslationException {
            when(mockProvider.translate(List.of("Test"), "en", "fr"))
                    .thenReturn(List.of("Essai"));

            TranslationService svc = service(600_000);
            svc.translate(List.of("Test"), "en", "fr"); // fills cache
            assertEquals(1, svc.cacheSize());

            svc.clearCache();
            assertEquals(0, svc.cacheSize());
        }

        @Test
        @DisplayName("cacheSize increments with new unique entries")
        void cacheSizeTracking() throws TranslationException {
            when(mockProvider.translate(anyList(), anyString(), anyString()))
                    .thenAnswer(inv -> {
                        List<String> in = inv.getArgument(0);
                        List<String> out = new java.util.ArrayList<>();
                        for (String s : in) out.add("translated-" + s);
                        return out;
                    });

            TranslationService svc = service(600_000);
            svc.translate(List.of("A"), "en", "fr");
            svc.translate(List.of("B"), "en", "fr");
            svc.translate(List.of("C"), "en", "fr");

            assertEquals(3, svc.cacheSize());
        }
    }

    // =========================================================================
    //  Batching — only cache-miss items reach the provider
    // =========================================================================

    @Nested
    @DisplayName("Batching (partial cache hit)")
    class BatchingTests {

        @Test
        @DisplayName("already-cached items are not sent to the provider again")
        void partialCacheHitReducesProviderBatch() throws TranslationException {
            TranslationService svc = service(600_000);

            // Pre-warm cache: translate "Alpha" alone
            when(mockProvider.translate(List.of("Alpha"), "en", "es"))
                    .thenReturn(List.of("Alfa"));
            svc.translate(List.of("Alpha"), "en", "es");

            // Now translate a batch of two; only "Beta" should go to provider
            when(mockProvider.translate(List.of("Beta"), "en", "es"))
                    .thenReturn(List.of("Beta-es"));

            List<String> result = svc.translate(List.of("Alpha", "Beta"), "en", "es");

            assertEquals(2, result.size());
            assertEquals("Alfa",    result.get(0)); // from cache
            assertEquals("Beta-es", result.get(1)); // from provider

            // Provider received only "Beta", not "Alpha"
            verify(mockProvider, never()).translate(
                    argThat(l -> l.contains("Alpha") && l.size() > 1),
                    anyString(), anyString());
            verify(mockProvider, times(1)).translate(List.of("Beta"), "en", "es");
        }

        @Test
        @DisplayName("result order is preserved when mixing cache hits and misses")
        void resultOrderPreservedWithMixedCacheState() throws TranslationException {
            TranslationService svc = service(600_000);

            // Pre-warm cache for index 0 and 2
            when(mockProvider.translate(List.of("A"), "en", "fr")).thenReturn(List.of("a-fr"));
            when(mockProvider.translate(List.of("C"), "en", "fr")).thenReturn(List.of("c-fr"));
            svc.translate(List.of("A"), "en", "fr");
            svc.translate(List.of("C"), "en", "fr");
            svc.clearCache(); // clear after warming, but add selectively below...
            // redo independently so only A is cached
            when(mockProvider.translate(List.of("A"), "en", "fr")).thenReturn(List.of("a-fr"));
            svc.translate(List.of("A"), "en", "fr"); // A in cache

            // Provider called for B and C misses
            when(mockProvider.translate(List.of("B", "C"), "en", "fr"))
                    .thenReturn(List.of("b-fr", "c-fr"));

            List<String> result = svc.translate(List.of("A", "B", "C"), "en", "fr");

            assertEquals(List.of("a-fr", "b-fr", "c-fr"), result);
        }
    }

    // =========================================================================
    //  Validation
    // =========================================================================

    @Nested
    @DisplayName("Input validation")
    class ValidationTests {

        @Test
        @DisplayName("null texts list throws TranslationException")
        void nullTextsThrows() {
            TranslationService svc = service(60_000);
            assertThrows(TranslationException.class,
                    () -> svc.translate(null, "en", "fr"));
        }

        @Test
        @DisplayName("null targetLang throws TranslationException")
        void nullTargetLangThrows() {
            TranslationService svc = service(60_000);
            assertThrows(TranslationException.class,
                    () -> svc.translate(List.of("hello"), "en", null));
        }

        @Test
        @DisplayName("unsupported target language throws TranslationException")
        void unsupportedTargetLangThrows() {
            TranslationService svc = service(60_000);
            // "xx" is not in SUPPORTED
            assertThrows(TranslationException.class,
                    () -> svc.translate(List.of("hello"), "en", "xx"));
        }

        @Test
        @DisplayName("unsupported source language (non-auto) throws TranslationException")
        void unsupportedSourceLangThrows() {
            TranslationService svc = service(60_000);
            assertThrows(TranslationException.class,
                    () -> svc.translate(List.of("hello"), "zz", "fr"));
        }

        @Test
        @DisplayName("batch size exceeding maxTextsPerRequest throws TranslationException")
        void tooManyTextsThrows() {
            // service built with maxTextsPerRequest=3
            TranslationService svc = new TranslationService(mockProvider, 60_000, 100, 3, 500);
            List<String> bigBatch = List.of("a", "b", "c", "d"); // 4 > 3
            assertThrows(TranslationException.class,
                    () -> svc.translate(bigBatch, "en", "fr"));
        }

        @Test
        @DisplayName("provider size mismatch throws TranslationException")
        void providerSizeMismatchThrows() throws TranslationException {
            // Provider returns fewer results than inputs
            when(mockProvider.translate(anyList(), anyString(), anyString()))
                    .thenReturn(List.of("only-one")); // input will be 2 texts

            TranslationService svc = service(60_000);
            assertThrows(TranslationException.class,
                    () -> svc.translate(List.of("hello", "world"), "en", "fr"));
        }
    }

    // =========================================================================
    //  Edge cases
    // =========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("blank/whitespace texts are passed through unchanged without calling provider")
        void blankTextsPassThrough() throws TranslationException {
            TranslationService svc = service(60_000);
            // Mix of blank and non-blank
            when(mockProvider.translate(List.of("Hello"), "en", "fr"))
                    .thenReturn(List.of("Bonjour"));

            List<String> result = svc.translate(
                    Arrays.asList("Hello", "   ", null), "en", "fr");

            assertEquals(3, result.size());
            assertEquals("Bonjour", result.get(0));
            assertEquals("   ",     result.get(1)); // whitespace untouched
            assertNull(result.get(2));               // null pass-through

            // Provider only received "Hello"
            verify(mockProvider, times(1)).translate(List.of("Hello"), "en", "fr");
        }

        @Test
        @DisplayName("text longer than maxCharsPerText is truncated before provider call")
        void longTextIsTruncated() throws TranslationException {
            // service with maxCharsPerText = 10
            TranslationService svc =
                    new TranslationService(mockProvider, 60_000, 100, 50, 10);

            String longInput = "Hello, this is a very long string";
            String truncated = "Hello, thi"; // first 10 chars

            when(mockProvider.translate(List.of(truncated), "en", "fr"))
                    .thenReturn(List.of("Bonjour"));

            List<String> result = svc.translate(List.of(longInput), "en", "fr");

            assertEquals("Bonjour", result.get(0));
            // Provider received truncated version
            verify(mockProvider).translate(List.of(truncated), "en", "fr");
        }

        @Test
        @DisplayName("detectLanguage delegates to provider")
        void detectLanguageDelegates() throws TranslationException {
            when(mockProvider.detectLanguage("Hola mundo")).thenReturn("es");

            TranslationService svc = service(60_000);
            assertEquals("es", svc.detectLanguage("Hola mundo"));
        }

        @Test
        @DisplayName("detectLanguage with null text returns 'unknown' without calling provider")
        void detectLanguageNullReturnsUnknown() throws TranslationException {
            TranslationService svc = service(60_000);
            assertEquals("unknown", svc.detectLanguage(null));
            verify(mockProvider, never()).detectLanguage(anyString());
        }

        @Test
        @DisplayName("all-blank list returns correctly sized list without calling provider")
        void allBlankList() throws TranslationException {
            TranslationService svc = service(60_000);
            List<String> result = svc.translate(
                    Arrays.asList("", "  ", null), "en", "hi");

            assertEquals(3, result.size());
            verify(mockProvider, never()).translate(anyList(), anyString(), anyString());
        }
    }
}
