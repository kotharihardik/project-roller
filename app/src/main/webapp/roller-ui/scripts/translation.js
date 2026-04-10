/**
 * translation.js — In-page translation widget for Roller Weblogger
 *
 * ── FEATURES ──────────────────────────────────────────────────────────────
 *
 * LAYOUT-SAFE DOM APPROACH
 *   A TreeWalker visits only TEXT_NODE descendants of the content container.
 *   It never touches element nodes, so tags, classes, IDs, href/src
 *   attributes, CSS layouts and images are completely untouched.
 *   Each node's .nodeValue is replaced (not innerHTML), so XSS is impossible.
 *
 * CLIENT-SIDE CACHING  (Task 5B)
 *   After a successful translation the result is saved in localStorage:
 *     Key  : "roller_tx|<page-url>|<target-lang>"
 *     Value: { fingerprint, provider, entries:[{orig,tx}], savedAt }
 *
 *   On every page load the widget checks localStorage.  If a cache entry
 *   exists for this URL + the user's last-used language it:
 *     1. Computes the page's current content fingerprint.
 *     2. If fingerprint MATCHES  -> applies all cached translations instantly
 *        (zero API calls).
 *     3. If fingerprint DIFFERS  -> partial invalidation:
 *          * Text nodes whose original text is unchanged -> reuse cached tx.
 *          * New / changed text nodes                   -> batch to API only.
 *        Only the truly changed nodes incur API cost.
 *
 *   "Significant content" for fingerprinting = text inside headings
 *   (h1-h4) and primary content elements (article, [class*=entry],
 *   [class*=post], [class*=content]).  Navigation, footer, sidebar text
 *   is excluded — cosmetic/structural edits do NOT trigger retranslation.
 *
 *   LAST-USED CONFIG is persisted separately so auto-apply works across
 *   different pages:
 *     Key  : "roller_tx_config"
 *     Value: { lang, source, provider }
 *
 * BATCH PROCESSING
 *   Texts are sent in batches of BATCH_SIZE (180) to stay well under
 *   the server's 200-text limit.  Progress is shown in the status bar.
 */
(function () {
    'use strict';

    /* ------------------------------------------------------------------ */
    /* Constants                                                            */
    /* ------------------------------------------------------------------ */
    var TRANSLATE_URL   = buildTranslateUrl();
    var CONTENT_ROOT_ID = 'roller-translation-content';
    var BATCH_SIZE      = 180;
    var CACHE_TTL_MS    = 7 * 24 * 3600 * 1000;   /* 7 days */
    var LS_CONFIG_KEY   = 'roller_tx_config';
    var DEFAULT_LANGS   = ['en', 'hi', 'gu', 'te', 'mr', 'pa'];

    function buildTranslateUrl() {
        var contextPath = '';
        var currentScript = document.currentScript;
        var scriptSrc = currentScript && currentScript.src ? currentScript.src : '';

        if (!scriptSrc) {
            var scripts = document.getElementsByTagName('script');
            for (var i = scripts.length - 1; i >= 0; i--) {
                var src = scripts[i].src || '';
                if (src.indexOf('/roller-ui/scripts/translation.js') !== -1) {
                    scriptSrc = src;
                    break;
                }
            }
        }

        var marker = '/roller-ui/scripts/translation.js';
        var idx = scriptSrc.indexOf(marker);
        if (idx !== -1) {
            var before = scriptSrc.substring(0, idx);
            var start = before.indexOf('/', before.indexOf('//') + 2);
            contextPath = start !== -1 ? before.substring(start) : '';
        }

        return (contextPath || '') + '/roller-services/translate';
    }

    /* Regex to detect Struts2 tooltip trigger text (e.g. '------|------'). */
    var ONLY_DASHES = /^[-|/\s]+$/;

    /* Selectors for "significant content" used in fingerprinting.
       Changes to these trigger (partial) retranslation.
       Navigation / sidebar / footer changes do NOT. */
    var SIGNIFICANT_SELECTORS = [
        'h1','h2','h3','h4',
        'article',
        '[class*="entry-content"]','[class*="post-content"]',
        '[class*="blog-content"]','[class*="weblog-content"]',
        '.entryContent','.postContent','.entry-body','.post-body'
    ].join(',');

    var SKIP_TAGS = new Set([
        'SCRIPT','STYLE','NOSCRIPT','CODE','PRE',
        'TEXTAREA','INPUT',
        'SVG','MATH','CANVAS','IFRAME',
        /* Struts2 renders small validation state nodes ("ok", "*") inside these: */
        'IMG','ACRONYM'
    ]);

    var LANG_NAMES = {
        'en': 'English',
        'hi': '\u0939\u093f\u0928\u094d\u0926\u0940 (Hindi)',
        'gu': '\u0a97\u0ac1\u0a9c\u0ab0\u0abe\u0aa4\u0ac0 (Gujarati)',
        'mr': '\u092e\u0930\u093e\u0920\u0940 (Marathi)',
        'te': '\u0c24\u0c46\u0c32\u0c41\u0c17\u0c41 (Telugu)',
        'pa': '\u0a2a\u0a70\u0a1c\u0a3e\u0a2c\u0a40 (Punjabi)'
    };

    function getSourceLanguageLabel(code) {
        if (code === 'en') return 'English';
        return LANG_NAMES[code];
    }

    var ENGLISH_LANG_NAMES = {
        'en': 'English',
        'hi': 'Hindi',
        'gu': 'Gujarati',
        'te': 'Telugu',
        'mr': 'Marathi',
        'pa': 'Punjabi'
    };

    function getEnglishLanguageName(code) {
        var key = (code || '').toLowerCase();
        return ENGLISH_LANG_NAMES[key] || key || 'Unknown';
    }

    function formatLanguagePair(sourceLang, targetLang) {
        return '(' + getEnglishLanguageName(sourceLang) + ' -> ' + getEnglishLanguageName(targetLang) + ')';
    }

    /* ------------------------------------------------------------------ */
    /* Module state                                                         */
    /* ------------------------------------------------------------------ */
    var _textNodes    = [];
    var _translated   = false;
    var _translating  = false;
    var _providerName = '';
    var _supportedLangs = [];
    var _runtimeProviders = ['sarvam', 'gemini'];

    function sanitizeSupportedLanguages(languages) {
        var incoming = Array.isArray(languages) ? languages : [];
        return DEFAULT_LANGS.filter(function (code) {
            return incoming.indexOf(code) !== -1;
        });
    }

    /* ================================================================== */
    /* INITIALISATION                                                      */
    /* ================================================================== */
    document.addEventListener('DOMContentLoaded', function () {
        ensureContentRoot();
        try {
            fetchMetadata(function (provider, languages, providers) {
                    _providerName   = provider;
                    _runtimeProviders = (providers && providers.length) ? providers : _runtimeProviders;
                    _supportedLangs = sanitizeSupportedLanguages(languages);
                    if (_supportedLangs.length === 0) _supportedLangs = DEFAULT_LANGS.slice();
                    injectWidget();
                    autoApplyFromCache();
                });
        } catch (e) {
            _providerName   = 'sarvam';
                _runtimeProviders = ['sarvam', 'gemini'];
                _supportedLangs = DEFAULT_LANGS.slice();
            injectWidget();
            autoApplyFromCache();
        }
    });

    function ensureContentRoot() {
        if (document.getElementById(CONTENT_ROOT_ID)) return;

        if (document.body && !document.body.id) {
            document.body.id = CONTENT_ROOT_ID;
            return;
        }

        if (document.body) {
            var fallback = document.createElement('div');
            fallback.id = CONTENT_ROOT_ID;
            while (document.body.firstChild) {
                fallback.appendChild(document.body.firstChild);
            }
            document.body.appendChild(fallback);
        }
    }

    /* ================================================================== */
    /* METADATA FETCH                                                      */
    /* ================================================================== */
    function fetchMetadata(callback) {
        if (typeof fetch !== 'function') {
            callback('unknown', DEFAULT_LANGS.slice());
            return;
        }
        fetch(TRANSLATE_URL, { method: 'GET', headers: { 'Accept': 'application/json' } })
            .then(function (r) { return parseJsonResponse(r, 'metadata'); })
            .then(function (d) {
                callback(d.provider || 'unknown',
                         d.languages || DEFAULT_LANGS.slice(),
                         d.providers || ['sarvam','gemini']);
            })
            .catch(function () {
                callback('sarvam', DEFAULT_LANGS.slice(), ['sarvam','gemini']);
            });
    }

    /* ================================================================== */
    /* WIDGET INJECTION                                                    */
    /* ================================================================== */
    function injectWidget() {
        if (document.getElementById('roller-translate-bar')) return;

        var container = document.getElementById(CONTENT_ROOT_ID);
        if (!container) return;

        /* Don't render the bar if no valid provider is configured. */
        if(!_providerName || _providerName.toLowerCase() === 'unknown') return;

        var savedConfig = loadConfig();
        var initialLang = (savedConfig && savedConfig.lang) || null;
        var initialSource = (savedConfig && savedConfig.source) || 'en';
        var initialProvider = (savedConfig && savedConfig.provider) || _providerName || 'sarvam';

        var bar = document.createElement('div');
        bar.id = 'roller-translate-bar';
        bar.setAttribute('style',
            'padding:8px 12px;background:#f0f4ff;border:1px solid #c8d8ff;' +
            'border-radius:4px;margin-bottom:12px;display:flex;' +
            'align-items:center;flex-wrap:wrap;gap:8px;font-size:13px;' +
            'font-family:sans-serif;');

        var badge = document.createElement('span');
        badge.id = 'roller-provider-badge';
        badge.setAttribute('style', 'color:#555;');
        badge.textContent = 'Translate via ';
        bar.appendChild(badge);

        var providerLabel = document.createElement('label');
        providerLabel.htmlFor = 'roller-provider-select';
        providerLabel.textContent = 'Provider:';
        providerLabel.setAttribute('style', 'margin-left:8px;color:#333;');
        bar.appendChild(providerLabel);

        var providerSelect = document.createElement('select');
        providerSelect.id = 'roller-provider-select';
        providerSelect.setAttribute('style', 'padding:4px 8px;border:1px solid #aac;border-radius:3px;');
        (_runtimeProviders || ['sarvam', 'gemini']).forEach(function (providerCode) {
            if (!providerCode) return;
            var opt = document.createElement('option');
            opt.value = providerCode;
            opt.textContent = getProviderDisplayName(providerCode);
            providerSelect.appendChild(opt);
        });
        if (providerSelect.options.length === 0) {
            ['sarvam', 'gemini'].forEach(function (providerCode) {
                var opt = document.createElement('option');
                opt.value = providerCode;
                opt.textContent = getProviderDisplayName(providerCode);
                providerSelect.appendChild(opt);
            });
        }
        providerSelect.value = initialProvider;
        _providerName = providerSelect.value || initialProvider;
        bar.appendChild(providerSelect);

        var sourceLabel = document.createElement('label');
        sourceLabel.htmlFor = 'roller-source-lang-select';
        sourceLabel.textContent = 'From:';
        sourceLabel.setAttribute('style', 'margin-left:8px;color:#333;');
        bar.appendChild(sourceLabel);

        var sourceSelect = document.createElement('select');
        sourceSelect.id = 'roller-source-lang-select';
        sourceSelect.setAttribute('style', 'padding:4px 8px;border:1px solid #aac;border-radius:3px;');
        _supportedLangs.forEach(function (code) {
            if (!getSourceLanguageLabel(code)) return;
            var opt = document.createElement('option');
            opt.value = code;
            opt.textContent = getSourceLanguageLabel(code);
            sourceSelect.appendChild(opt);
        });
        sourceSelect.value = (_supportedLangs.indexOf(initialSource) !== -1) ? initialSource : 'en';
        bar.appendChild(sourceSelect);

        var label = document.createElement('label');
        label.htmlFor = 'roller-lang-select';
        label.textContent = 'Into:';
        label.setAttribute('style', 'margin-left:8px;color:#333;');
        bar.appendChild(label);

        var select = document.createElement('select');
        select.id = 'roller-lang-select';
        select.setAttribute('style', 'padding:4px 8px;border:1px solid #aac;border-radius:3px;');
        _supportedLangs.forEach(function (code) {
            if (!LANG_NAMES[code]) return;
            var opt = document.createElement('option');
            opt.value       = code;
            opt.textContent = LANG_NAMES[code];
            select.appendChild(opt);
        });
        if (initialLang && LANG_NAMES[initialLang]) {
            select.value = initialLang;
        } else {
            var def = _supportedLangs.find(function (l) { return l !== sourceSelect.value && LANG_NAMES[l]; });
            if (!def && _supportedLangs.length > 0) def = _supportedLangs[0];
            if (def) select.value = def;
        }
        bar.appendChild(select);

        var btnTranslate = document.createElement('button');
        btnTranslate.id = 'roller-translate-btn';
        btnTranslate.textContent = 'Translate';
        btnTranslate.setAttribute('style',
            'padding:5px 14px;background:#3a6bc9;color:#fff;border:none;' +
            'border-radius:3px;cursor:pointer;font-size:13px;');
        bar.appendChild(btnTranslate);

        var btnReset = document.createElement('button');
        btnReset.id = 'roller-reset-btn';
        btnReset.textContent = 'Reset';
        btnReset.setAttribute('style',
            'padding:5px 14px;background:#888;color:#fff;border:none;' +
            'border-radius:3px;cursor:pointer;font-size:13px;display:none;');
        bar.appendChild(btnReset);

        var cacheBadge = document.createElement('span');
        cacheBadge.id = 'roller-cache-badge';
        cacheBadge.setAttribute('style', 'font-size:11px;color:#777;font-style:italic;display:none;');
        bar.appendChild(cacheBadge);

        var statusEl = document.createElement('span');
        statusEl.id = 'roller-translate-status';
        statusEl.setAttribute('style', 'color:#555;font-style:italic;');
        bar.appendChild(statusEl);

        btnTranslate.addEventListener('click', function () {
            onTranslateClick(providerSelect.value, sourceSelect.value, select.value, btnTranslate, btnReset, statusEl, cacheBadge);
        });
        providerSelect.addEventListener('change', function () {
            _providerName = providerSelect.value;
            badge.textContent = 'Translate via ';
        });
        btnReset.addEventListener('click', function () {
            onResetClick(statusEl, btnReset, cacheBadge);
        });

        hideDashNodes();

        if (container === document.body) {
            document.body.insertBefore(bar, document.body.firstChild);
        } else if (container.parentNode) {
            container.parentNode.insertBefore(bar, container);
        }
    }

    /* ================================================================== */
    /* AUTO-APPLY FROM CACHE  (Task 5B)                                   */
    /* ================================================================== */
    function autoApplyFromCache() {
        var config = loadConfig();
        if (!config || !config.lang) return;

        _providerName = (config.provider || _providerName || 'sarvam').toLowerCase();
        var sourceLang = (config.source || 'en').toLowerCase();

        var cacheKey = makeCacheKey(config.lang, _providerName, sourceLang);
        var cached   = loadCache(cacheKey);
        if (!cached) return;

        var btnTranslate = document.getElementById('roller-translate-btn');
        var btnReset     = document.getElementById('roller-reset-btn');
        var statusEl     = document.getElementById('roller-translate-status');
        var cacheBadge   = document.getElementById('roller-cache-badge');
        var select       = document.getElementById('roller-lang-select');
        var sourceSelect = document.getElementById('roller-source-lang-select');
        var providerSelect = document.getElementById('roller-provider-select');
        if (select) select.value = config.lang;
        if (sourceSelect) sourceSelect.value = sourceLang;
        if (providerSelect) providerSelect.value = _providerName;

        var root = document.getElementById(CONTENT_ROOT_ID);
        if (!root) return;

        /* Collect from full body so sidebar, navigation, feeds and calendar
           are also translated, not just the main content container. */
        _textNodes = collectTextNodes(document.body).map(function (n) {
            return { node: n, original: n.nodeValue };
        }).concat(collectButtonInputs(document.body));
        /* DEBUG: log collected nodes (console + localStorage + downloadable .log) */
        try { logCollectedNodes(_textNodes); } catch (e) { console.log('roller-translate: logCollectedNodes error', e); }
        /* Debug: when user presses Translate, also log collected nodes
           to console, localStorage and provide a downloadable .log file */
        try {
            var _collectedSnapshot_click = _textNodes.map(function (item, idx) {
                return {
                    index: idx,
                    tag: item.node && item.node.parentElement ? item.node.parentElement.tagName : 'UNKNOWN',
                    text: (item.original || '').trim()
                };
            });
            try { console.groupCollapsed('roller-translate: collected nodes'); console.table(_collectedSnapshot_click); console.groupEnd(); } catch (e) {}
            try { localStorage.setItem('roller_tx_last_collected', JSON.stringify(_collectedSnapshot_click)); } catch (e) {}
            var now_click = new Date().toISOString().replace(/[:.]/g, '-');
            var fname_click = 'translation-collect-' + now_click + '.log';
            var lines_click = _collectedSnapshot_click.map(function (c) { return c.index + ' | ' + c.tag + ' | ' + c.text; });
            if (lines_click.length === 0) lines_click.push('NO_NODES_COLLECTED');
        } catch (e) { console.log('roller-translate: collect-click-log error', e); }
        /* Debug: snapshot collected nodes (index | parent tag | text)
           - saved to localStorage key `roller_tx_last_collected`
           - triggers a small downloadable log file for inspection */
        try {
            var _collectedSnapshot = _textNodes.map(function (item, idx) {
                return {
                    index: idx,
                    tag: item.node && item.node.parentElement ? item.node.parentElement.tagName : 'UNKNOWN',
                    text: (item.original || '').trim()
                };
            });
            try { localStorage.setItem('roller_tx_last_collected', JSON.stringify(_collectedSnapshot)); } catch (e) {}

            var now = new Date().toISOString().replace(/[:.]/g, '-');
            var fname = 'translation-collect-' + now + '.log';
            var lines = _collectedSnapshot.map(function (c) { return c.index + ' | ' + c.tag + ' | ' + c.text; });
            if (lines.length === 0) lines.push('NO_NODES_COLLECTED');
        } catch (e) { console.log('roller-translate: collect-log error', e); }
        if (_textNodes.length === 0) return;

        var currentFP = computeFingerprint(root);

        if (currentFP === cached.fingerprint && hasCompleteCacheCoverage(_textNodes, cached.entries, sourceLang, config.lang)) {
            /* Perfect cache hit — zero API calls */
            applyTranslationsFromCache(_textNodes, cached.entries);
            hideDashNodes();
            _translated = true;
            if (btnReset)     btnReset.style.display = '';
            if (cacheBadge)   showCacheBadge(cacheBadge, 'full');
            showStatus(statusEl,
                'Auto-translated ' + formatLanguagePair(sourceLang, config.lang),
                 '#2a7a2a');
        } else {
            /* Content changed — partial retranslation */
            showStatus(statusEl, ' Content changed, retranslating', '#555');
            if (btnTranslate) btnTranslate.disabled = true;
            _translating = true;

            partialTranslate(_textNodes, cached.entries, sourceLang, config.lang, statusEl, _providerName)
                .then(function (allTx) {
                    applyTranslations(_textNodes, allTx);
                    hideDashNodes();
                    _translated  = true;
                    _translating = false;
                    if (btnTranslate) btnTranslate.disabled = false;
                    if (btnReset)     btnReset.style.display = '';
                    saveCache(cacheKey, {
                        fingerprint: currentFP,
                        provider:    _providerName,
                        entries:     buildCacheEntries(_textNodes, allTx),
                        savedAt:     Date.now()
                    });
                    if (cacheBadge) showCacheBadge(cacheBadge, 'partial');
                    showStatus(statusEl,
                        ' Auto-translated ' + formatLanguagePair(sourceLang, config.lang) );
                })
                .catch(function (err) {
                    _translating = false;
                    if (btnTranslate) btnTranslate.disabled = false;
                    showStatus(statusEl, ' ' + err.message, '#c00');
                });
        }
    }

    /* ================================================================== */
    /* TRANSLATE BUTTON HANDLER                                            */
    /* ================================================================== */
    function onTranslateClick(providerName, sourceLang, targetLang, btnTranslate, btnReset, statusEl, cacheBadge) {
        if (_translating) return;
        if (_translated) doReset(cacheBadge);
        _providerName = (providerName || _providerName || 'sarvam').toLowerCase();
        sourceLang = (sourceLang || 'auto').toLowerCase();

        if (sourceLang === targetLang) {
            showStatus(statusEl,
                ' Source and target languages are the same ' + formatLanguagePair(sourceLang, targetLang) + '. No translation needed.',
                '#888');
            return;
        }

        var root = document.getElementById(CONTENT_ROOT_ID);
        if (!root) {
            showStatus(statusEl, ' Translation container not found.', '#c00');
            return;
        }

        /* Collect from full body so sidebar, navigation, feeds and calendar
           are also translated, not just the main content container. */
        _textNodes = collectTextNodes(document.body).map(function (n) {
            return { node: n, original: n.nodeValue };
        }).concat(collectButtonInputs(document.body));
        if (_textNodes.length === 0) {
            showStatus(statusEl, 'Nothing to translate.', '#888');
            return;
        }

        var cacheKey  = makeCacheKey(targetLang, _providerName, sourceLang);
        var cached    = loadCache(cacheKey);
        var currentFP = computeFingerprint(root);

        if (cached && cached.fingerprint === currentFP && hasCompleteCacheCoverage(_textNodes, cached.entries, sourceLang, targetLang)) {
            /* Full cache hit */
            applyTranslationsFromCache(_textNodes, cached.entries);
            hideDashNodes();
            _translated = true;
            btnReset.style.display = '';
            showCacheBadge(cacheBadge, 'full');
            showStatus(statusEl,
                ' Translated ' + formatLanguagePair(sourceLang, targetLang) +
                ' (from cache)', '#2a7a2a');
            saveConfig(targetLang, sourceLang, _providerName);
            return;
        }

        _translating = true;
        btnTranslate.disabled = true;

        var txPromise;
        if (cached) {
            showCacheBadge(cacheBadge, 'partial');
            txPromise = partialTranslate(_textNodes, cached.entries, sourceLang, targetLang, statusEl, _providerName);
        } else {
            hideCacheBadge(cacheBadge);
            txPromise = translateInBatches(
                _textNodes.map(function (t) {
                    return normalizeForTranslation(t);
                }),
                sourceLang, targetLang, statusEl, _providerName);
        }

        txPromise
            .then(function (allTx) {
                applyTranslations(_textNodes, allTx);
                hideDashNodes();
                _translated  = true;
                _translating = false;
                btnTranslate.disabled = false;
                btnReset.style.display = '';
                saveCache(cacheKey, {
                    fingerprint: currentFP,
                    provider:    _providerName,
                    entries:     buildCacheEntries(_textNodes, allTx),
                    savedAt:     Date.now()
                });
                saveConfig(targetLang, sourceLang, _providerName);
                showStatus(statusEl,
                    ' Translated ' + formatLanguagePair(sourceLang, targetLang), '#2a7a2a');
            })
            .catch(function (err) {
                _translating = false;
                btnTranslate.disabled = false;
                showStatus(statusEl, ' ' + err.message, '#c00');
            });
    }

    /* ================================================================== */
    /* PARTIAL TRANSLATION  (Task 5B)                                     */
    /* ================================================================== */
    /**
     * Splits nodes into: cache-hit (reuse) vs cache-miss (API call).
     * Returns a flat array of translations aligned with textNodes.
     */
    function partialTranslate(textNodes, cachedEntries, sourceLang, targetLang, statusEl, providerName) {
        var lookup = {};
        cachedEntries.forEach(function (e) { lookup[e.orig] = e.tx; });

        var allTx         = new Array(textNodes.length);
        var batchIndices  = [];
        var batchTexts    = [];

        textNodes.forEach(function (item, i) {
            var hit = lookup[item.original];
            // Also try trimmed original for cache lookup
            if (hit === undefined) hit = lookup[item.isButton ? item.original : item.original.trim()];
            if (hit !== undefined && shouldReuseCachedTranslation(item, hit, sourceLang, targetLang)) {
                allTx[i] = hit;
            } else {
                batchIndices.push(i);
                batchTexts.push(normalizeForTranslation(item));
            }
        });

        if (batchTexts.length === 0) return Promise.resolve(allTx);

        return translateInBatches(batchTexts, sourceLang, targetLang, statusEl, providerName)
            .then(function (translated) {
                batchIndices.forEach(function (nodeIdx, pos) {
                    allTx[nodeIdx] = translated[pos];
                });
                return allTx;
            });
    }

    /* ================================================================== */
    /* BATCH TRANSLATION                                                   */
    /* ================================================================== */
    function translateInBatches(texts, sourceLang, targetLang, statusEl, providerName) {
        var batches = [];
        for (var i = 0; i < texts.length; i += BATCH_SIZE) {
            batches.push(texts.slice(i, i + BATCH_SIZE));
        }
        var results = [];
        var total   = batches.length;
        var chain   = Promise.resolve();

        batches.forEach(function (batch, idx) {
            chain = chain.then(function () {
                showStatus(statusEl,
                    ' Translating ' + (idx + 1) + ' / ' + total, '#555');
                return postTranslate(batch, sourceLang, targetLang, providerName)
                    .then(function (tx) { results = results.concat(tx); });
            });
        });
        return chain.then(function () { return results; });
    }

    /* ================================================================== */
    /* RESET                                                               */
    /* ================================================================== */
    function onResetClick(statusEl, btnReset, cacheBadge) {
        var sourceSelect = document.getElementById('roller-source-lang-select');
        if (sourceSelect) sourceSelect.value = 'en';
        var select = document.getElementById('roller-lang-select');
        if (select) select.value = 'en';
        doReset(cacheBadge);
        clearConfig();
        statusEl.textContent    = '';
        btnReset.style.display  = 'none';
    }

    function doReset(cacheBadge) {
        _textNodes.forEach(function (item) {
            if (item.isButton) {
                if (item.isInputBtn) {
                    item.node.value = item.original;
                } else {
                    item.node.textContent = item.original;
                }
            } else {
                item.node.nodeValue = item.original;
            }
        });
        _translated = false;
        hideCacheBadge(cacheBadge);
    }

    /* ================================================================== */
    /* DOM HELPERS                                                         */
    /* ================================================================== */

    /**
     * Removes Struts2 tooltip trigger text (e.g. '------|------') from the DOM
     * by clearing the text node value — NOT by hiding the parent element, which
     * would also erase adjacent label/input content.
     */
    function hideDashNodes() {
        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
        while (walker.nextNode()) {
            var t = walker.currentNode.nodeValue.trim();
            if (t.length > 3 && ONLY_DASHES.test(t)) {
                walker.currentNode.nodeValue = '';
            }
        }
    }

    /* Matches at least one letter (ASCII/Unicode) or digit. */
    var HAS_LETTER = /[a-zA-Z\u00C0-\uFFFF]/;
    var HAS_TRANSLATABLE = /[a-zA-Z\u00C0-\uFFFF0-9]/;

    /* Struts2 xhtml theme tooltip trigger classes — rendered as '------|------' spans. */
    var TOOLTIP_CLASSES = ['ttClassDisabled', 'ttClassEnabled', 'tooltip'];

    function hasTooltipClass(el) {
        if (!el || !el.className) return false;
        var cls = typeof el.className === 'string' ? el.className : '';
        return TOOLTIP_CLASSES.some(function (c) { return cls.indexOf(c) >= 0; });
    }

    function inTooltipAncestor(node) {
        var el = node.parentElement;
        while (el) {
            if (hasTooltipClass(el)) return true;
            el = el.parentElement;
        }
        return false;
    }

    /* Skip nodes that are inside screen-reader-only or otherwise hidden
       UI elements (e.g. <span class="sr-only">Toggle navigation</span>). */
    var HIDDEN_A11Y_CLASSES = ['sr-only', 'screen-reader-only', 'visually-hidden', 'u-visually-hidden', 'sr_only'];

    function hasHiddenA11yClass(el) {
        if (!el || !el.className) return false;
        var cls = typeof el.className === 'string' ? el.className : '';
        return HIDDEN_A11Y_CLASSES.some(function (c) { return cls.indexOf(c) >= 0; });
    }

    function inHiddenAncestor(node) {
        var el = node.parentElement;
        while (el) {
            try {
                if (hasHiddenA11yClass(el)) return true;
                if (el.hasAttribute && el.hasAttribute('hidden')) return true;
                if (el.getAttribute && el.getAttribute('aria-hidden') === 'true') return true;
            } catch (e) {}
            el = el.parentElement;
        }
        return false;
    }

    function collectTextNodes(root) {
        var nodes  = [];
        var walker = document.createTreeWalker(
            root, NodeFilter.SHOW_TEXT,
            {
                acceptNode: function (node) {
                    if (SKIP_TAGS.has(node.parentElement && node.parentElement.tagName))
                        return NodeFilter.FILTER_REJECT;
                    /* Reject nodes inside screen-reader / hidden ancestors. */
                    if (inHiddenAncestor(node))
                        return NodeFilter.FILTER_REJECT;
                    var t = node.nodeValue.trim();
                    if (!t)
                        return NodeFilter.FILTER_SKIP;
                    /* Skip purely decorative nodes like '------|------' (Struts2 tooltip triggers). */
                    if (!HAS_TRANSLATABLE.test(t))
                        return NodeFilter.FILTER_SKIP;
                    /* Skip Struts2 tooltip trigger elements entirely. */
                    if (inTooltipAncestor(node))
                        return NodeFilter.FILTER_REJECT;
                    return NodeFilter.FILTER_ACCEPT;
                }
            }, false);
        while (walker.nextNode()) { nodes.push(walker.currentNode); }
        return nodes;
    }

    /* ------------------------------------------------------------------ */
    /* Debug helper: log collected nodes (console + localStorage + download) */
    /* ------------------------------------------------------------------ */
    function logCollectedNodes(textNodes) {
        try {
            var snapshot = textNodes.map(function(item, idx) {
                return {
                    index: idx,
                    tag: item.node && item.node.parentElement ? item.node.parentElement.tagName : 'UNKNOWN',
                    text: (item.original || '').trim()
                };
            });

            try { console.group('roller-translate: collected nodes'); console.table(snapshot); console.groupEnd(); } catch (e) {}

            try { localStorage.setItem('roller_tx_last_collected', JSON.stringify(snapshot)); } catch (e) {}

            var now = new Date().toISOString().replace(/[:.]/g, '-');
            var filename = 'translation-collect-' + now + '.log';
            var lines = snapshot.map(function(c) { return c.index + ' | ' + c.tag + ' | ' + c.text; });
            if (lines.length === 0) lines.push('NO_NODES_COLLECTED');
        } catch (e) {
            console.error('roller-translate debug error:', e);
        }
    }

    /**
     * Collect <input type="submit">, <input type="button"> and <button> elements
     * whose visible label text should be translated.
     * Returns items compatible with the _textNodes format:
     *   { node, original, isButton, isInputBtn }
     */
    function collectButtonInputs(root) {
        var items = [];
        var WIDGET_IDS = ['roller-translate-btn', 'roller-reset-btn'];
        // <input type="submit"> and <input type="button">
        var inputs = root.querySelectorAll('input[type="submit"], input[type="button"]');
        Array.prototype.forEach.call(inputs, function (el) {
            if (WIDGET_IDS.indexOf(el.id) !== -1) return;
            var val = (el.value || '').trim();
            if (val && HAS_TRANSLATABLE.test(val)) {
                items.push({ node: el, original: val, isButton: true, isInputBtn: true });
            }
        });
        // <button> elements
        var buttons = root.querySelectorAll('button');
        Array.prototype.forEach.call(buttons, function (el) {
            if (WIDGET_IDS.indexOf(el.id) !== -1) return;
            var val = (el.textContent || '').trim();
            if (val && HAS_TRANSLATABLE.test(val)) {
                items.push({ node: el, original: val, isButton: true, isInputBtn: false });
            }
        });
        return items;
    }

    /** Apply one translated string to either a text node or a button/input. */
    function applyTextToNode(item, text) {
        if (!text) text = item.original;
        var digitLocalizedText = localizeDigitsForTargetLang(text);
        if (item.isButton) {
            if (item.isInputBtn) {
                item.node.value = digitLocalizedText;
            } else {
                item.node.textContent = digitLocalizedText;
            }
        } else {
            var saneText = sanitizeTranslatedText(item.original, digitLocalizedText);
            var phraseSafeText = applyKnownPhraseFallback(item.original, saneText);
            var safeText = preserveLabelQualifier(item, item.original, phraseSafeText);
            item.node.nodeValue = restoreOriginalWhitespace(item.original, safeText);
        }
    }

    function applyKnownPhraseFallback(original, translated) {
        if (typeof original !== 'string' || typeof translated !== 'string') return translated;

        var src = original.trim().toLowerCase();
        var tx = translated.trim().toLowerCase();
        var lang = getCurrentTargetLang();

        var phraseMap = {
            'powered by apache roller': {
                hi: '\u0905\u092a\u093e\u091a\u0947 \u0930\u094b\u0932\u0930 \u0926\u094d\u0935\u093e\u0930\u093e \u0938\u0902\u091a\u093e\u0932\u093f\u0924',
                gu: '\u0a85\u0aaa\u0abe\u0a9a\u0ac7 \u0ab0\u0acb\u0ab2\u0ab0 \u0aa6\u0acd\u0ab5\u0abe\u0ab0\u0abe \u0ab8\u0a82\u0a9a\u0abe\u0ab2\u0abf\u0aa4',
                mr: '\u0905\u092a\u093e\u091a\u0947 \u0930\u094b\u0932\u0930\u0926\u094d\u0935\u093e\u0930\u0947 \u091a\u093e\u0932\u0935\u0932\u0947\u0932\u0947',
                te: '\u0c05\u0c2a\u0c3e\u0c1a\u0c40 \u0c30\u0c4b\u0c32\u0c30\u0c4d \u0c26\u0c4d\u0c35\u0c3e\u0c30\u0c3e \u0c28\u0c21\u0c3f\u0c2a\u0c3f\u0c02\u0c1a\u0c2c\u0c21\u0c3f\u0c28\u0c26\u0c3f',
                pa: '\u0a05\u0a2a\u0a3e\u0a1a\u0a47 \u0a30\u0a4b\u0a32\u0a30 \u0a26\u0a41\u0a06\u0a30\u0a3e \u0a1a\u0a32\u0a3e\u0a07\u0a06 \u0a17\u0a3f\u0a06'
            }
        };

        if (src === 'powered by apache roller') {
            if (tx === src && phraseMap[src] && phraseMap[src][lang]) {
                return phraseMap[src][lang];
            }
        }

        var keyMap = {
            'starttime': {
                hi: '\u092a\u094d\u0930\u093e\u0930\u0902\u092d \u0938\u092e\u092f',
                gu: '\u0ab6\u0ab0\u0ac2\u0a86\u0aa4\u0aa8\u0acb \u0ab8\u0aae\u0aaf',
                mr: '\u0938\u0941\u0930\u0941\u0935\u093e\u0924\u0940\u091a\u0940 \u0935\u0947\u0933',
                te: '\u0c2a\u0c4d\u0c30\u0c3e\u0c30\u0c02\u0c2d \u0c38\u0c2e\u0c2f\u0c02',
                pa: '\u0a36\u0a41\u0a30\u0a42\u0a06\u0a24\u0a40 \u0a38\u0a2e\u0a3e\u0a02'
            },
            'logout': {
                gu: '\u0ab2\u0acb\u0a97\u0a86\u0a89\u0a9f',
                mr: '\u0932\u0949\u0917\u0906\u0909\u091f \u0915\u0930\u093e.'
            },
            'frontpage': {
                mr: '\u092e\u0941\u0916\u092a\u0943\u0937\u094d\u0920'
            },
            'weblog': {
                mr: '\u092c\u094d\u0932\u0949\u0917'
            },
            'newentry': {
                mr: '\u0928\u0935\u0940\u0928 \u0928\u094b\u0902\u0926'
            },
            'settings': {
                mr: '\u0938\u0947\u091f\u093f\u0902\u0917\u094d\u091c'
            },
            'comments': {
                mr: '\u091f\u093f\u092a\u094d\u092a\u0923\u094d\u092f\u093e'
            },
            'links': {
                mr: '\u0926\u0941\u0935\u0947'
            },
            'navigation': {
                mr: '\u0928\u0947\u0935\u094d\u0939\u093f\u0917\u0947\u0936\u0928'
            },
            'createedit': {
                gu: '\u0ab2\u0a96\u0abe\u0aa3 \u0a85\u0aa8\u0ac7 \u0ab8\u0a82\u0aaa\u0abe\u0aa6\u0aa8',
                mr: '\u0928\u093f\u0930\u094d\u092e\u093e\u0923 \u0906\u0923\u093f \u0938\u0902\u092a\u093e\u0926\u0928'
            },
            'createeditnewentry': {
                gu: '\u0ab2\u0a96\u0abe\u0aa3: \u0aa8\u0ab5\u0ac0 \u0a8f\u0aa8\u0acd\u0a9f\u0acd\u0ab0\u0ac0',
                mr: '\u092e\u091c\u0915\u0942\u0930: \u0928\u0935\u0940\u0928 \u092a\u094d\u0930\u0935\u0947\u0936'
            },
            'createeditentries': {
                gu: '\u0ab2\u0a96\u0abe\u0aa3: \u0aaa\u0acd\u0ab0\u0ab5\u0ac7\u0ab6',
                mr: '\u0928\u093f\u0930\u094d\u092e\u093e\u0923 \u0906\u0923\u093f \u0938\u0902\u092a\u093e\u0926\u0928: \u092a\u094d\u0930\u0935\u093f\u0937\u094d\u091f\u094d\u092f\u093e'
            },
            'createeditcomments': {
                gu: '\u0ab2\u0a96\u0abe\u0aa3: \u0a9f\u0abf\u0aaa\u0acd\u0aaa\u0aa3\u0ac0\u0a93',
                mr: '\u092e\u091c\u0915\u0942\u0930: \u091f\u093f\u092a\u094d\u092a\u0923\u094d\u092f\u093e'
            },
            'createeditcategories': {
                gu: '\u0ab2\u0a96\u0abe\u0aa3: \u0ab6\u0acd\u0ab0\u0ac7\u0aa3\u0ac0\u0a93',
                mr: '\u092e\u091c\u0915\u0942\u0930: \u0936\u094d\u0930\u0947\u0923\u094d\u092f\u093e'
            },
            'createeditblogroll': {
                gu: '\u0ab2\u0a96\u0abe\u0aa3: \u0aac\u0acd\u0ab2\u0acb\u0a97\u0ab0\u0acb\u0ab2',
                mr: '\u092e\u091c\u0915\u0942\u0930: \u092c\u094d\u0932\u0949\u0917\u0930\u094b\u0932'
            },
            'createeditmediafiles': {
                gu: '\u0ab0\u0a9a\u0aa8\u0abe \u0a85\u0aa8\u0ac7 \u0ab8\u0a82\u0aaa\u0abe\u0aa6\u0abf\u0aa4 \u0a95\u0ab0\u0acb: \u0aae\u0ac0\u0aa1\u0abf\u0aaf\u0abe \u0aab\u0abe\u0a87\u0ab2\u0acb',
                mr: '\u092e\u091c\u0915\u0942\u0930, \u092a\u094d\u0930\u0924\u093f\u092e\u093e, \u0927\u094d\u0935\u0928\u0940 \u0906\u0923\u093f \u0926\u0943\u0915\u094d-\u0927\u094d\u0935\u0928\u093f\u0915\u0940'
            },
            'preferences': {
                gu: '\u0aaa\u0ab8\u0a82\u0aa6\u0a97\u0ac0\u0a93',
                mr: '\u092a\u094d\u0930\u093e\u0927\u093e\u0928\u094d\u092f\u0947'
            },
            'preferencessettings': {
                gu: '\u0aaa\u0ab8\u0a82\u0aa6\u0a97\u0ac0\u0a93: \u0ab8\u0ac7\u0a9f\u0abf\u0a82\u0a97\u0acd\u0ab8',
                mr: '\u092a\u094d\u0930\u093e\u0927\u093e\u0928\u094d\u092f\u0947: \u0938\u0947\u091f\u093f\u0902\u0917\u094d\u091c'
            },
            'preferencesmembers': {
                gu: '\u0aaa\u0ab8\u0a82\u0aa6\u0a97\u0ac0\u0a93: \u0ab8\u0aad\u0acd\u0aaf\u0acb',
                mr: '\u092a\u094d\u0930\u093e\u0927\u093e\u0928\u094d\u092f\u0947: \u0938\u0926\u0938\u094d\u092f'
            },
            'preferencespings': {
                gu: '\u0aaa\u0ab8\u0a82\u0aa6\u0a97\u0ac0\u0a93: \u0aaa\u0abf\u0a82\u0a97\u0acd\u0ab8',
                mr: '\u092a\u094d\u0930\u093e\u0927\u093e\u0928\u094d\u092f\u0947: \u092a\u093f\u0902\u0917\u094d\u091c'
            },
            'preferencesmaintenance': {
                gu: '\u0aaa\u0ab8\u0a82\u0aa6\u0a97\u0ac0\u0a93: \u0a9c\u0abe\u0ab3\u0ab5\u0aa3\u0ac0',
                mr: '\u092a\u094d\u0930\u093e\u0927\u093e\u0928\u094d\u092f\u0947: \u0926\u0947\u0916\u092d\u093e\u0932'
            }
        };

        var key = src.replace(/[^a-z0-9]+/g, '');
        if (keyMap[key] && tx === src && keyMap[key][lang]) {
            return keyMap[key][lang];
        }

        var calendarFixed = localizeCalendarToken(original, lang);
        if (calendarFixed) {
            return calendarFixed;
        }

        return translated;
    }

    function localizeCalendarToken(original, lang) {
        if (typeof original !== 'string') return '';

        var trimmed = original.trim();
        if (!trimmed) return '';

        var monthYear = localizeMonthYearToken(trimmed, lang);
        if (monthYear) return monthYear;

        var suffixMatch = trimmed.match(/[\s\.,:;!?-]*$/);
        var suffix = suffixMatch ? suffixMatch[0] : '';
        var core = trimmed.replace(/[\s\.,:;!?-]*$/, '');
        var normalized = core.toLowerCase();

        var dayMap = {
            hi: {
                sun: '\u0930\u0935\u093f', monday: '\u0938\u094b\u092e', mon: '\u0938\u094b\u092e', tuesday: '\u092e\u0902\u0917\u0932', tue: '\u092e\u0902\u0917\u0932', tues: '\u092e\u0902\u0917\u0932',
                wednesday: '\u092c\u0941\u0927', wed: '\u092c\u0941\u0927', thursday: '\u0917\u0941\u0930\u0941', thu: '\u0917\u0941\u0930\u0941', thur: '\u0917\u0941\u0930\u0941', thurs: '\u0917\u0941\u0930\u0941',
                friday: '\u0936\u0941\u0915\u094d\u0930', fri: '\u0936\u0941\u0915\u094d\u0930', saturday: '\u0936\u0928\u093f', sat: '\u0936\u0928\u093f', sunday: '\u0930\u0935\u093f'
            },
            gu: {
                sun: '\u0ab0\u0ab5\u0abf', monday: '\u0ab8\u0acb\u0aae', mon: '\u0ab8\u0acb\u0aae', tuesday: '\u0aae\u0a82\u0a97\u0ab3', tue: '\u0aae\u0a82\u0a97\u0ab3', tues: '\u0aae\u0a82\u0a97\u0ab3',
                wednesday: '\u0aac\u0ac1\u0aa7', wed: '\u0aac\u0ac1\u0aa7', thursday: '\u0a97\u0ac1\u0ab0\u0ac1', thu: '\u0a97\u0ac1\u0ab0\u0ac1', thur: '\u0a97\u0ac1\u0ab0\u0ac1', thurs: '\u0a97\u0ac1\u0ab0\u0ac1',
                friday: '\u0ab6\u0ac1\u0a95\u0acd\u0ab0', fri: '\u0ab6\u0ac1\u0a95\u0acd\u0ab0', saturday: '\u0ab6\u0aa8\u0abf', sat: '\u0ab6\u0aa8\u0abf', sunday: '\u0ab0\u0ab5\u0abf'
            },
            mr: {
                sun: '\u0930\u0935\u093f', monday: '\u0938\u094b\u092e', mon: '\u0938\u094b\u092e', tuesday: '\u092e\u0902\u0917\u0933', tue: '\u092e\u0902\u0917\u0933', tues: '\u092e\u0902\u0917\u0933',
                wednesday: '\u092c\u0941\u0927', wed: '\u092c\u0941\u0927', thursday: '\u0917\u0941\u0930\u0941', thu: '\u0917\u0941\u0930\u0941', thur: '\u0917\u0941\u0930\u0941', thurs: '\u0917\u0941\u0930\u0941',
                friday: '\u0936\u0941\u0915\u094d\u0930', fri: '\u0936\u0941\u0915\u094d\u0930', saturday: '\u0936\u0928\u093f', sat: '\u0936\u0928\u093f', sunday: '\u0930\u0935\u093f'
            },
            te: {
                sun: '\u0c06\u0c26\u0c3f', monday: '\u0c38\u0c4b\u0c2e', mon: '\u0c38\u0c4b\u0c2e', tuesday: '\u0c2e\u0c02\u0c17\u0c33', tue: '\u0c2e\u0c02\u0c17\u0c33', tues: '\u0c2e\u0c02\u0c17\u0c33',
                wednesday: '\u0c2c\u0c41\u0c27', wed: '\u0c2c\u0c41\u0c27', thursday: '\u0c17\u0c41\u0c30\u0c41', thu: '\u0c17\u0c41\u0c30\u0c41', thur: '\u0c17\u0c41\u0c30\u0c41', thurs: '\u0c17\u0c41\u0c30\u0c41',
                friday: '\u0c36\u0c41\u0c15\u0c4d\u0c30', fri: '\u0c36\u0c41\u0c15\u0c4d\u0c30', saturday: '\u0c36\u0c28\u0c3f', sat: '\u0c36\u0c28\u0c3f', sunday: '\u0c06\u0c26\u0c3f'
            },
            pa: {
                sun: '\u0a10\u0a24', monday: '\u0a38\u0a4b\u0a2e', mon: '\u0a38\u0a4b\u0a2e', tuesday: '\u0a2e\u0a70\u0a17\u0a32', tue: '\u0a2e\u0a70\u0a17\u0a32', tues: '\u0a2e\u0a70\u0a17\u0a32',
                wednesday: '\u0a2c\u0a41\u0a71\u0a27', wed: '\u0a2c\u0a41\u0a71\u0a27', thursday: '\u0a17\u0a41\u0a30\u0a42', thu: '\u0a17\u0a41\u0a30\u0a42', thur: '\u0a17\u0a41\u0a30\u0a42', thurs: '\u0a17\u0a41\u0a30\u0a42',
                friday: '\u0a36\u0a41\u0a71\u0a15\u0a30', fri: '\u0a36\u0a41\u0a71\u0a15\u0a30', saturday: '\u0a36\u0a28\u0a40', sat: '\u0a36\u0a28\u0a40', sunday: '\u0a10\u0a24\u0a35\u0a3e\u0a30'
            }
        };

        if (dayMap[lang] && dayMap[lang][normalized]) {
            return dayMap[lang][normalized] + suffix;
        }

        return '';
    }

    function localizeMonthYearToken(input, lang) {
        var m = input.match(/^([A-Za-z]+)\s+(\d{4})$/);
        if (!m) return '';

        var month = m[1].toLowerCase();
        var year = m[2];

        var monthMap = {
            hi: {
                january: '\u091c\u0928\u0935\u0930\u0940', february: '\u092b\u093c\u0930\u0935\u0930\u0940', march: '\u092e\u093e\u0930\u094d\u091a', april: '\u0905\u092a\u094d\u0930\u0948\u0932',
                may: '\u092e\u0908', june: '\u091c\u0942\u0928', july: '\u091c\u0941\u0932\u093e\u0908', august: '\u0905\u0917\u0938\u094d\u0924',
                september: '\u0938\u093f\u0924\u0902\u092c\u0930', october: '\u0905\u0915\u094d\u091f\u0942\u092c\u0930', november: '\u0928\u0935\u0902\u092c\u0930', december: '\u0926\u093f\u0938\u0902\u092c\u0930'
            },
            gu: {
                january: '\u0a9c\u0abe\u0aa8\u0acd\u0aaf\u0ac1\u0a86\u0ab0\u0ac0', february: '\u0aab\u0ac7\u0aac\u0acd\u0ab0\u0ac1\u0a86\u0ab0\u0ac0', march: '\u0aae\u0abe\u0ab0\u0acd\u0a9a', april: '\u0a8f\u0aaa\u0acd\u0ab0\u0abf\u0ab2',
                may: '\u0aae\u0ac7', june: '\u0a9c\u0ac2\u0aa8', july: '\u0a9c\u0ac1\u0ab2\u0abe\u0a88', august: '\u0a91\u0a97\u0ab8\u0acd\u0a9f',
                september: '\u0ab8\u0aaa\u0acd\u0a9f\u0ac7\u0aae\u0acd\u0aac\u0ab0', october: '\u0a91\u0a95\u0acd\u0a9f\u0acb\u0aac\u0ab0', november: '\u0aa8\u0ab5\u0ac7\u0aae\u0acd\u0aac\u0ab0', december: '\u0aa1\u0abf\u0ab8\u0ac7\u0aae\u0acd\u0aac\u0ab0'
            },
            mr: {
                january: '\u091c\u093e\u0928\u0947\u0935\u093e\u0930\u0940', february: '\u092b\u0947\u092c\u094d\u0930\u0941\u0935\u093e\u0930\u0940', march: '\u092e\u093e\u0930\u094d\u091a', april: '\u090f\u092a\u094d\u0930\u093f\u0932',
                may: '\u092e\u0947', june: '\u091c\u0942\u0928', july: '\u091c\u0941\u0932\u0948', august: '\u0911\u0917\u0938\u094d\u091f',
                september: '\u0938\u092a\u094d\u091f\u0947\u0902\u092c\u0930', october: '\u0911\u0915\u094d\u091f\u094b\u092c\u0930', november: '\u0928\u094b\u0935\u094d\u0939\u0947\u0902\u092c\u0930', december: '\u0921\u093f\u0938\u0947\u0902\u092c\u0930'
            },
            te: {
                january: '\u0c1c\u0c28\u0c35\u0c30\u0c3f', february: '\u0c2b\u0c3f\u0c2c\u0c4d\u0c30\u0c35\u0c30\u0c3f', march: '\u0c2e\u0c3e\u0c30\u0c4d\u0c1a\u0c3f', april: '\u0c0f\u0c2a\u0c4d\u0c30\u0c3f\u0c32\u0c4d',
                may: '\u0c2e\u0c47', june: '\u0c1c\u0c42\u0c28\u0c4d', july: '\u0c1c\u0c42\u0c32\u0c48', august: '\u0c06\u0c17\u0c38\u0c4d\u0c1f\u0c41',
                september: '\u0c38\u0c46\u0c2a\u0c4d\u0c1f\u0c46\u0c02\u0c2c\u0c30\u0c4d', october: '\u0c05\u0c15\u0c4d\u0c1f\u0c4b\u0c2c\u0c30\u0c4d', november: '\u0c28\u0c35\u0c02\u0c2c\u0c30\u0c4d', december: '\u0c21\u0c3f\u0c38\u0c46\u0c02\u0c2c\u0c30\u0c4d'
            },
            pa: {
                january: '\u0a1c\u0a28\u0a35\u0a30\u0a40', february: '\u0a2b\u0a3c\u0a30\u0a35\u0a30\u0a40', march: '\u0a2e\u0a3e\u0a30\u0a1a', april: '\u0a05\u0a2a\u0a4d\u0a30\u0a48\u0a32',
                may: '\u0a2e\u0a08', june: '\u0a1c\u0a42\u0a28', july: '\u0a1c\u0a41\u0a32\u0a3e\u0a08', august: '\u0a05\u0a17\u0a38\u0a24',
                september: '\u0a38\u0a24\u0a70\u0a2c\u0a30', october: '\u0a05\u0a15\u0a24\u0a42\u0a2c\u0a30', november: '\u0a28\u0a35\u0a70\u0a2c\u0a30', december: '\u0a26\u0a38\u0a70\u0a2c\u0a30'
            }
        };

        if (monthMap[lang] && monthMap[lang][month]) {
            return monthMap[lang][month] + ' ' + year;
        }
        return '';
    }

    function sanitizeTranslatedText(original, translated) {
        if (typeof original !== 'string') return translated;
        if (typeof translated !== 'string') return original;

        var trimmedOriginal = original.trim();
        var trimmedTranslated = translated.trim();

        if (!trimmedTranslated) return original;
        if (ONLY_DASHES.test(trimmedTranslated)) return original;

        if (HAS_LETTER.test(trimmedOriginal) && !HAS_LETTER.test(trimmedTranslated)) {
            return original;
        }

        return translated;
    }

    function preserveLabelQualifier(item, original, translated) {
        if (typeof original !== 'string' || typeof translated !== 'string') return translated;
        if (!item || !item.node || !item.node.parentElement) return translated;

        var parentTag = item.node.parentElement.tagName;
        if (parentTag !== 'LABEL') return translated;

        var originalQualifiers = original.match(/\([^)]*\)/g);
        if (!originalQualifiers || originalQualifiers.length === 0) return translated;

        var sameAsOriginal = translated.trim() === original.trim();
        if (sameAsOriginal) {
            var baseTranslated = findNearestBaseLabel(item, original) || localizeBaseWord(original);
            if (baseTranslated) {
                return baseTranslated + ' ' + localizeQualifiers(originalQualifiers);
            }
        }

        var hasQualifierInTranslation = /\([^)]*\)/.test(translated);
        if (hasQualifierInTranslation) return translated;

        return translated.trim() + ' ' + localizeQualifiers(originalQualifiers);
    }

    function findNearestBaseLabel(item, original) {
        var baseOriginal = original.replace(/\([^)]*\)/g, '').trim();
        if (!baseOriginal) return '';

        var currentLabel = item.node.parentElement;
        var prev = currentLabel.previousElementSibling;
        while (prev) {
            if (prev.tagName === 'LABEL') {
                var t = (prev.textContent || '').trim();
                if (t && !/\([^)]*\)/.test(t) && t.toLowerCase() !== 'into:') {
                    return t;
                }
                break;
            }
            prev = prev.previousElementSibling;
        }
        return '';
    }

    function localizeBaseWord(original) {
        var base = original.replace(/\([^)]*\)/g, '').replace(/[\s\.:]+$/g, '').trim();
        var normalized = base.toLowerCase();
        var lang = getCurrentTargetLang();
        var map = {
            hi: { password: '\u092a\u093e\u0938\u0935\u0930\u094d\u0921' },
            gu: { password: '\u0aaa\u0abe\u0ab8\u0ab5\u0ab0\u0acd\u0aa1' },
            mr: { password: '\u092a\u093e\u0938\u0935\u0930\u094d\u0921' },
            te: { password: '\u0c2a\u0c3e\u0c38\u0c4d\u0c35\u0c30\u0c4d\u0c21\u0c4d' },
            pa: { password: '\u0a2a\u0a3e\u0a38\u0a35\u0a30\u0a21' },
            ta: { password: '\u0b95\u0b9f\u0bb5\u0bc1\u0b9a\u0bcd\u0b9a\u0bca\u0bb2\u0bcd' },
            kn: { password: '\u0caa\u0cbe\u0cb8\u0ccd\u0cb5\u0cb0\u0ccd\u0ca1\u0ccd' }
        };
        if (map[lang] && map[lang][normalized]) return map[lang][normalized];
        return '';
    }

    function localizeQualifiers(qualifiers) {
        var lang = getCurrentTargetLang();
        return qualifiers.map(function (q) {
            var inner = q.replace(/^\(|\)$/g, '').trim();
            var localized = localizeQualifierWord(inner, lang);
            return '(' + localized + ')';
        }).join(' ');
    }

    function getCurrentTargetLang() {
        var select = document.getElementById('roller-lang-select');
        return select && select.value ? select.value : 'en';
    }

    function localizeDigitsForTargetLang(text) {
        if (typeof text !== 'string' || !/[0-9]/.test(text)) return text;

        var lang = getCurrentTargetLang();
        var digitMaps = {
            hi: ['\u0966','\u0967','\u0968','\u0969','\u096A','\u096B','\u096C','\u096D','\u096E','\u096F'],
            mr: ['\u0966','\u0967','\u0968','\u0969','\u096A','\u096B','\u096C','\u096D','\u096E','\u096F'],
            te: ['\u0C66','\u0C67','\u0C68','\u0C69','\u0C6A','\u0C6B','\u0C6C','\u0C6D','\u0C6E','\u0C6F'],
            gu: ['\u0AE6','\u0AE7','\u0AE8','\u0AE9','\u0AEA','\u0AEB','\u0AEC','\u0AED','\u0AEE','\u0AEF'],
            pa: ['\u0A66','\u0A67','\u0A68','\u0A69','\u0A6A','\u0A6B','\u0A6C','\u0A6D','\u0A6E','\u0A6F']
        };

        var targetDigits = digitMaps[lang];
        if (!targetDigits) return text;

        return text.replace(/[0-9]/g, function (d) {
            return targetDigits[d.charCodeAt(0) - 48] || d;
        });
    }

    function localizeQualifierWord(word, lang) {
        if (!word) return word;
        var normalized = word.toLowerCase().trim();
        var map = {
            hi: { confirm: '\u092a\u0941\u0937\u094d\u091f\u093f \u0915\u0930\u0947\u0902' },
            gu: { confirm: '\u0aaa\u0ac1\u0ab7\u0acd\u0a9f\u0abf \u0a95\u0ab0\u0acb' },
            mr: { confirm: '\u092a\u0941\u0937\u094d\u091f\u0940 \u0915\u0930\u093e' },
            te: { confirm: '\u0c27\u0c43\u0c35\u0c40\u0c15\u0c30\u0c3f\u0c02\u0c1a\u0c02\u0c21\u0c3f' },
            pa: { confirm: '\u0a2a\u0a41\u0a38\u0a3c\u0a1f\u0a40 \u0a15\u0a30\u0a4b' },
            ta: { confirm: '\u0b89\u0bb1\u0bc1\u0ba4\u0bbf\u0baa\u0bcd\u0baa\u0b9f\u0bc1\u0ba4\u0bcd\u0ba4\u0bb5\u0bc1\u0bae\u0bcd' },
            kn: { confirm: '\u0ca6\u0cc3\u0ca2\u0cc0\u0c95\u0cb0\u0cbf\u0cb8\u0cbf' }
        };
        if (map[lang] && map[lang][normalized]) return map[lang][normalized];
        return word;
    }

    function normalizeForTranslation(item) {
        if (!item || typeof item.original !== 'string') return '';
        return item.isButton ? item.original : item.original.trim();
    }

    function restoreOriginalWhitespace(original, translated) {
        if (typeof original !== 'string') return translated;
        if (typeof translated !== 'string') return original;

        var match = original.match(/^(\s*)([\s\S]*?)(\s*)$/);
        if (!match) return translated;

        var leading = match[1] || '';
        var trailing = match[3] || '';
        return leading + translated.trim() + trailing;
    }

    function applyTranslations(textNodes, translations) {
        textNodes.forEach(function (item, i) {
            applyTextToNode(item, translations[i] || item.original);
        });
    }

    function applyTranslationsFromCache(textNodes, cachedEntries) {
        var lookup = {};
        cachedEntries.forEach(function (e) { lookup[e.orig] = e.tx; });
        textNodes.forEach(function (item) {
            var tx = lookup[item.original];
            if (tx !== undefined) applyTextToNode(item, tx);
        });
    }

    function hasCompleteCacheCoverage(textNodes, cachedEntries, sourceLang, targetLang) {
        if (!Array.isArray(textNodes) || !Array.isArray(cachedEntries)) return false;

        var lookup = {};
        cachedEntries.forEach(function (e) {
            if (e && typeof e.orig === 'string') lookup[e.orig] = e.tx;
        });

        for (var i = 0; i < textNodes.length; i++) {
            var item = textNodes[i];
            var original = item && typeof item.original === 'string' ? item.original : '';
            if (lookup[original] !== undefined && shouldReuseCachedTranslation(item, lookup[original], sourceLang, targetLang)) {
                continue;
            }

            var normalized = item && item.isButton ? original : original.trim();
            if (lookup[normalized] === undefined || !shouldReuseCachedTranslation(item, lookup[normalized], sourceLang, targetLang)) {
                return false;
            }
        }
        return true;
    }

    function shouldReuseCachedTranslation(item, cachedTx, sourceLang, targetLang) {
        if (cachedTx === undefined || cachedTx === null) return false;

        var original = item && typeof item.original === 'string' ? item.original : '';
        var translated = String(cachedTx);

        var src = (sourceLang || 'auto').toLowerCase();
        var tgt = (targetLang || '').toLowerCase();
        if (!tgt || src === tgt) return true;

        if (!HAS_TRANSLATABLE.test(original)) return true;

        var normOriginal = original.trim().toLowerCase();
        var normTranslated = translated.trim().toLowerCase();
        if (!normOriginal || !normTranslated) return true;

        return normOriginal !== normTranslated;
    }

    function buildCacheEntries(textNodes, translations) {
        return textNodes.map(function (item, i) {
            return { orig: item.original, tx: translations[i] || item.original };
        });
    }

    /* ================================================================== */
    /* CONTENT FINGERPRINTING  (Task 5B)                                  */
    /* ================================================================== */
    /**
     * Hashes "significant content" — headings + article/entry body only.
     * Navigation, sidebar, footer changes do NOT invalidate the cache.
     * Algorithm: djb2 (fast 32-bit hash, good enough for change detection).
     *
     * Significant change = ANY of:
     *   - Heading text (h1-h4) changes
     *   - Article / entry body text changes
     * Non-significant (cache preserved):
     *   - Navigation link text changes
     *   - Comment count / date-stamp updates
     *   - Sidebar widget changes
     */
    function computeFingerprint(root) {
        var sig = root.querySelectorAll(SIGNIFICANT_SELECTORS);
        var parts = sig.length > 0
            ? Array.prototype.map.call(sig, function (el) { return el.textContent.trim(); })
            : [root.textContent.trim()];
        return djb2(parts.join('\x00'));
    }

    function djb2(str) {
        var hash = 5381;
        for (var i = 0; i < str.length; i++) {
            hash = ((hash << 5) + hash) ^ str.charCodeAt(i);
            hash = hash >>> 0;
        }
        return hash.toString(16);
    }

    /* ================================================================== */
    /* LOCALSTORAGE CACHE  (Task 5B)                                      */
    /* ================================================================== */
    function makeCacheKey(lang, providerName, sourceLang) {
        var provider = (providerName || _providerName || 'sarvam').toLowerCase();
        var source = (sourceLang || 'auto').toLowerCase();
        return 'roller_tx|' + location.href + '|' + provider + '|' + source + '|' + lang;
    }

    function loadCache(key) {
        try {
            var raw = localStorage.getItem(key);
            if (!raw) return null;
            var obj = JSON.parse(raw);
            if (Date.now() - (obj.savedAt || 0) > CACHE_TTL_MS) {
                localStorage.removeItem(key);
                return null;
            }
            return obj;
        } catch (e) { return null; }
    }

    function saveCache(key, obj) {
        try {
            localStorage.setItem(key, JSON.stringify(obj));
        } catch (e) {
            evictOldestCacheEntries();
            try { localStorage.setItem(key, JSON.stringify(obj)); } catch (e2) {}
        }
    }

    function evictOldestCacheEntries() {
        var keys = [];
        for (var i = 0; i < localStorage.length; i++) {
            var k = localStorage.key(i);
            if (k && k.indexOf('roller_tx|') === 0) keys.push(k);
        }
        keys.sort(function (a, b) {
            var ta = 0, tb = 0;
            try { ta = JSON.parse(localStorage.getItem(a)).savedAt || 0; } catch (e) {}
            try { tb = JSON.parse(localStorage.getItem(b)).savedAt || 0; } catch (e) {}
            return ta - tb;
        });
        keys.slice(0, Math.ceil(keys.length / 2))
            .forEach(function (k) { localStorage.removeItem(k); });
    }

    function saveConfig(lang, sourceLang, providerName) {
        try {
            var provider = (providerName || _providerName || 'sarvam').toLowerCase();
            var source = (sourceLang || 'en').toLowerCase();
            localStorage.setItem(LS_CONFIG_KEY,
                JSON.stringify({ lang: lang, source: source, provider: provider }));
        } catch (e) {}
    }

    function clearConfig() {
        try { localStorage.removeItem(LS_CONFIG_KEY); } catch (e) {}
    }

    function loadConfig() {
        try {
            var raw = localStorage.getItem(LS_CONFIG_KEY);
            return raw ? JSON.parse(raw) : null;
        } catch (e) { return null; }
    }

    /* ================================================================== */
    /* API CALL                                                            */
    /* ================================================================== */
    function postTranslate(texts, sourceLang, targetLang, providerName) {
        var payload = {
            source: sourceLang,
            target: targetLang,
            provider: (providerName || _providerName || 'sarvam').toLowerCase(),
            texts: texts
        };

        return fetch(TRANSLATE_URL, {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify(payload)
        }).then(function (resp) {
            return parseJsonResponse(resp, 'translate').then(function (data) {
                if (!resp.ok)
                    throw new Error(data.error || 'Translation failed (HTTP ' + resp.status + ')');
                if (!data.translations || !Array.isArray(data.translations))
                    throw new Error('Unexpected server response format.');
                return data.translations;
            });
        });
    }

    function parseJsonResponse(resp, endpointName) {
        var contentType = (resp.headers && resp.headers.get('content-type')) || '';
        if (contentType.indexOf('application/json') === -1) {
            return resp.text().then(function (txt) {
                var snippet = (txt || '').trim().slice(0, 40);
                if (snippet) {
                    throw new Error('Translation API returned non-JSON (' + endpointName + '). ' +
                        'Check Roller setup/login state. Sample: ' + snippet);
                }
                throw new Error('Translation API returned non-JSON (' + endpointName + ').');
            });
        }
        return resp.json();
    }

    /* ================================================================== */
    /* UI UTILITIES                                                        */
    /* ================================================================== */
    function showStatus(el, msg, color) {
        if (!el) return;
        el.textContent = msg;
        el.style.color = color || '#555';
    }

    function showCacheBadge(el, type) {
        if (!el) return;
        el.style.display = '';
        if (type === 'full') {
            el.textContent = ' cached';
            el.style.color = '#2a7a2a';
        } else {
            el.textContent = ' partial cache';
            el.style.color = '#b07000';
        }
    }

    function hideCacheBadge(el) { if (el) el.style.display = 'none'; }

    function getProviderDisplayName(providerCode) {
        var key = (providerCode || '').toLowerCase();
        if (key === 'gemini') return 'Gemini';
        if (key === 'sarvam') return 'Sarvam';
        return providerCode ? providerCode.charAt(0).toUpperCase() + providerCode.slice(1) : 'AI';
    }

}());