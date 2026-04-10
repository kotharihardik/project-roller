package org.apache.roller.weblogger.business.translation;

import java.util.List;
import java.util.Set;

/**
 * Backward-compatibility wrapper that delegates every call to
 * {@link GeminiTranslationProvider}.
 *
 * <p>The old {@code RemoteTranslationProvider} class contained hardcoded
 * credentials, a public {@code configureGemini()} method that printed the
 * API key to stdout, an unbounded debug log file written to the user's home
 * directory, and ~80 lines of copy-pasted HTTP request logic. All of that has
 * been removed.</p>
 *
 * <p>Any deployment that still has {@code translation.provider=remote} in its
 * properties file will continue to work unchanged: this class accepts the same
 * constructor signature and routes all calls to the clean
 * {@link GeminiTranslationProvider} implementation.</p>
 *
 * @deprecated Use {@link GeminiTranslationProvider} directly by setting
 *             {@code translation.provider=gemini}.
 */
@Deprecated
public class RemoteTranslationProvider implements TranslationProvider {

    /** The real implementation — all logic lives here. */
    private final GeminiTranslationProvider delegate;

    /**
     * Constructs the provider — same constructor signature as the original
     * class so {@link TranslationService} requires no change to the
     * {@code "remote"} case.
     *
     * @param apiUrl         Gemini endpoint URL (or any compatible generative API).
     * @param apiKey         API key or {@code "Bearer <token>"}.
     * @param timeoutSeconds HTTP connect timeout.
     * @param model          Gemini model name (e.g. {@code "gemini-2.0-flash"}).
     */
    public RemoteTranslationProvider(String apiUrl, String apiKey,
                                     int timeoutSeconds, String model) {
        this.delegate = new GeminiTranslationProvider(apiUrl, apiKey, timeoutSeconds, model);
    }

    @Override
    public String getProviderName() {
        // Keep returning "gemini" so the UI shows the correct label.
        return delegate.getProviderName();
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return delegate.getSupportedLanguages();
    }

    @Override
    public List<String> translate(List<String> texts, String sourceLang, String targetLang)
            throws TranslationException {
        return delegate.translate(texts, sourceLang, targetLang);
    }

    @Override
    public String detectLanguage(String text) throws TranslationException {
        return delegate.detectLanguage(text);
    }
}
