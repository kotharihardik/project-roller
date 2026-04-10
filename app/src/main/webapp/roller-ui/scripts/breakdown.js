/**
 * breakdown.js — Conversation Breakdown UI
 *
 * Attaches click handlers to the "Generate Breakdown" buttons in the
 * Comments management page.  Calls BreakdownServlet via POST and renders
 * the returned themes + recap inside the #breakdown-panel div.
 *
 * Two buttons are wired:
 *   #btn-breakdown-keyword  → method=keyword  (TF-IDF, instant, no AI)
 *   #btn-breakdown-gemini   → method=gemini   (Gemini API, opt-in)
 */
(function () {
    'use strict';

    /* ------------------------------------------------------------------ */
    /* Resolve the servlet URL from the script's own src path             */
    /* ------------------------------------------------------------------ */
    function buildBreakdownUrl() {
        var contextPath = '';
        var scripts = document.getElementsByTagName('script');
        for (var i = scripts.length - 1; i >= 0; i--) {
            var src = scripts[i].src || '';
            if (src.indexOf('/roller-ui/scripts/breakdown.js') !== -1) {
                var marker = '/roller-ui/scripts/breakdown.js';
                var idx = src.indexOf(marker);
                if (idx !== -1) {
                    var before = src.substring(0, idx);
                    var start = before.indexOf('/', before.indexOf('//') + 2);
                    contextPath = start !== -1 ? before.substring(start) : '';
                }
                break;
            }
        }
        return (contextPath || '') + '/roller-services/breakdown';
    }

    var BREAKDOWN_URL = buildBreakdownUrl();

    /* ------------------------------------------------------------------ */
    /* Wire buttons once DOM is ready                                      */
    /* ------------------------------------------------------------------ */
    document.addEventListener('DOMContentLoaded', function () {
        wireButton('btn-breakdown-keyword', 'keyword');
        wireButton('btn-breakdown-gemini',  'gemini');
    });

    function wireButton(btnId, method) {
        var btn = document.getElementById(btnId);
        if (!btn) return;
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            var entryId = btn.getAttribute('data-entry-id');
            if (!entryId) {
                showError('No entry ID found. Please open a specific entry\'s comment page.');
                return;
            }
            requestBreakdown(entryId, method, btn);
        });
    }

    /* ------------------------------------------------------------------ */
    /* AJAX call                                                            */
    /* ------------------------------------------------------------------ */
    function requestBreakdown(entryId, method, triggerBtn) {
        var panel   = document.getElementById('breakdown-panel');
        var content = document.getElementById('breakdown-content');
        if (!panel || !content) return;

        // Show loading state
        setButtonsDisabled(true);
        panel.style.display = 'block';
        content.innerHTML   = '<p class="text-muted"><em>Generating breakdown&#8230;</em></p>';

        var body = 'entryId=' + encodeURIComponent(entryId) +
                   '&method='  + encodeURIComponent(method);

        var xhr = new XMLHttpRequest();
        xhr.open('POST', BREAKDOWN_URL, true);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.timeout = 60000; // 60 s (Gemini can be slow)

        xhr.onload = function () {
            setButtonsDisabled(false);
            try {
                var data = JSON.parse(xhr.responseText);
                if (xhr.status === 200) {
                    renderBreakdown(data, method, content);
                } else {
                    // Build debug-aware error message
                    var msg = data.error || ('HTTP ' + xhr.status);
                    var debugHtml = '<div class="alert alert-danger"><strong>Error:</strong> ' + esc(msg) + '</div>';

                    // Show any debug_ fields the server returned
                    var debugLines = [];
                    for (var key in data) {
                        if (data.hasOwnProperty(key) && key.indexOf('debug_') === 0) {
                            debugLines.push('<tr><td><code>' + esc(key) + '</code></td><td>' + esc(String(data[key])) + '</td></tr>');
                        }
                    }
                    if (debugLines.length > 0) {
                        debugHtml += '<table class="table table-condensed table-bordered" style="font-size:0.85em;margin-top:8px;">'
                            + '<thead><tr><th>Debug field</th><th>Value</th></tr></thead><tbody>'
                            + debugLines.join('') + '</tbody></table>';
                    }
                    content.innerHTML = debugHtml;
                }
            } catch (err) {
                showPanelError(content, 'Unexpected response from server: ' + err.message + ' | Raw: ' + xhr.responseText.substring(0, 200));
            }
        };

        xhr.onerror = function () {
            setButtonsDisabled(false);
            showPanelError(content, 'Network error. Please try again.');
        };

        xhr.ontimeout = function () {
            setButtonsDisabled(false);
            showPanelError(content, 'Request timed out. The AI may be busy — try again shortly.');
        };

        xhr.send(body);
    }

    /* ------------------------------------------------------------------ */
    /* Rendering                                                            */
    /* ------------------------------------------------------------------ */
    function renderBreakdown(data, method, container) {
        var html = '';

        // Strategy badge
        var badgeClass = method === 'gemini' ? 'label-primary' : 'label-default';
        var label      = method === 'gemini' ? 'AI (Gemini)' : 'Keyword Cluster';
        html += '<p><span class="label ' + badgeClass + '">' +
                esc(label) + '</span></p>';

        // Overall recap
        if (data.recap) {
            html += '<div class="alert alert-info" style="margin-bottom:12px;">' +
                    '<strong>Overall Recap:</strong> ' + esc(data.recap) +
                    '</div>';
        }

        // Themes
        if (data.themes && data.themes.length > 0) {
            html += '<h5 style="border-bottom:1px solid #ddd;padding-bottom:4px;">' +
                    'Major Themes</h5>';
            html += '<div class="list-group">';
            for (var i = 0; i < data.themes.length; i++) {
                var theme = data.themes[i];
                html += '<div class="list-group-item">';
                html += '<strong>' + esc(theme.name) + '</strong>';
                if (theme.representatives && theme.representatives.length > 0) {
                    html += '<ul style="margin-top:6px;margin-bottom:0;">';
                    for (var j = 0; j < theme.representatives.length; j++) {
                        html += '<li style="font-size:0.9em;color:#555;">' +
                                esc(theme.representatives[j]) + '</li>';
                    }
                    html += '</ul>';
                }
                html += '</div>';
            }
            html += '</div>';
        } else {
            html += '<p class="text-muted">No themes identified.</p>';
        }

        container.innerHTML = html;
    }

    function showPanelError(container, msg) {
        container.innerHTML = '<div class="alert alert-danger">' + esc(msg) + '</div>';
    }

    function showError(msg) {
        var content = document.getElementById('breakdown-content');
        var panel   = document.getElementById('breakdown-panel');
        if (panel)   panel.style.display = 'block';
        if (content) showPanelError(content, msg);
    }

    function setButtonsDisabled(disabled) {
        var ids = ['btn-breakdown-keyword', 'btn-breakdown-gemini'];
        for (var i = 0; i < ids.length; i++) {
            var b = document.getElementById(ids[i]);
            if (b) b.disabled = disabled;
        }
    }

    /* Simple HTML escaping — no dependencies on external libraries */
    function esc(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

}());
