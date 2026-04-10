package org.apache.roller.weblogger.business.translation;

import java.util.List;
import java.util.Set;

/**
 * Provider-agnostic contract for text translation services.
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>All language codes use <strong>ISO 639-1</strong> (e.g. {@code "en"},
 *       {@code "hi"}, {@code "es"}).  Each implementation is responsible for
 *       mapping these to whatever codes its remote API expects.</li>
 *   <li>The {@code texts} list is translated in order; the returned list has
 *       exactly the same size and index alignment.</li>
 *   <li>Implementations must be thread-safe – a single instance may be reused
 *       across concurrent HTTP requests.</li>
 * </ul>
 *
 * <h3>Adding a new provider</h3>
 * <ol>
 *   <li>Create a class in this package that implements {@code TranslationProvider}.</li>
 *   <li>Register its key in {@code roller.properties} under
 *       {@code translation.provider}.</li>
 *   <li>Register &amp; instantiate it in
 *       {@link TranslationService#buildProvider(String)}.</li>
 * </ol>
 *
 * <h3>Supported languages (minimum required)</h3>
 * {@code en}, {@code hi}, {@code ta}, {@code bn}, {@code fr} — every language
 * returned by {@link #getSupportedLanguages()} must work as both source and
 * target.
 */
public interface TranslationProvider {

    /**
    * Returns the short unique identifier for this provider, e.g.
    * {@code "sarvam"} or {@code "gemini"}/{@code "remote"}.  Used in configuration
     * and in the REST API's {@code provider} request field.
     */
    String getProviderName();

    /**
     * Returns the set of ISO 639-1 language codes this provider supports as
     * both source <em>and</em> target.  The UI uses this to populate the
     * language dropdown.
     */
    Set<String> getSupportedLanguages();

    /**
     * Translates a batch of texts from {@code sourceLang} to
     * {@code targetLang}.
     *
     * <p>Pass {@code "auto"} as {@code sourceLang} to request automatic
     * language detection.  When auto-detection is used the implementation
     * should attempt a best-effort detection; it must not throw simply because
     * the source language is unknown.</p>
     *
     * @param texts      non-null, non-empty list of plain-text strings.  Each
     *                   entry is a single text node extracted from the DOM.
     * @param sourceLang ISO 639-1 source language code, or {@code "auto"}.
     * @param targetLang ISO 639-1 target language code; must be in
     *                   {@link #getSupportedLanguages()}.
     * @return           translated strings in the same order as {@code texts};
     *                   never {@code null}, same size as input.
     * @throws TranslationException if the provider returns an error or the
     *                              HTTP request fails.
     */
    List<String> translate(List<String> texts, String sourceLang, String targetLang)
            throws TranslationException;

    /**
     * Detects the most likely language of {@code text} and returns its ISO
     * 639-1 code.
     *
     * <p>This is a <em>best-effort</em> operation.  Implementations that do
     * not support standalone detection may return {@code "unknown"}; they must
     * not throw {@link UnsupportedOperationException}.</p>
     *
     * @param text sample text to analyse (typically the first ~200 chars of
     *             the blog entry).
     * @return     ISO 639-1 language code, or {@code "unknown"}.
     * @throws TranslationException if the provider returns an error.
     */
    String detectLanguage(String text) throws TranslationException;
}
