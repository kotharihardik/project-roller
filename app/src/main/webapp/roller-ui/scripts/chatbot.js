
(function () {
    'use strict';

    // Configuration
    var API_BASE = (function () {
        // Derive context path from this script's own src attribute,
        var scripts = document.getElementsByTagName('script');
        var marker = '/roller-ui/scripts/chatbot.js';
        for (var i = 0; i < scripts.length; i++) {
            var src = scripts[i].src || '';
            var idx = src.indexOf(marker);
            if (idx !== -1) {
                var before = src.substring(0, idx);
                var slashAfterProto = before.indexOf('/', before.indexOf('//') + 2);
                var contextPath = slashAfterProto !== -1 ? before.substring(slashAfterProto) : '';
                return contextPath + '/roller-services/chatbot';
            }
        }
        // Fallback: assume /roller context
        return '/roller/roller-services/chatbot';
    })();

    var weblogId = null;
    var entryIdScope = null;

    // Initialization
    function init() {
        // Determine Weblog ID
        weblogId = window.ROLLER_CHATBOT_WEBLOG_ID
            || document.body.getAttribute('data-weblog-id')
            || null;
        entryIdScope = window.ROLLER_CHATBOT_ENTRY_ID || null;

        if (!weblogId) {
            console.warn('[Chatbot] No weblog ID found. Widget disabled.');
            return;
        }

        injectHTML();
        loadStrategies();
        bindEvents();
    }

 
    // Inject widget HTML into the page
    function injectHTML() {
        var html =
            // FAB button
            '<button id="chatbot-fab" title="Ask a question about this blog">' +
                '<span class="fab-icon">&#128172;</span>' +
                '<span class="chatbot-badge"></span>' +
            '</button>' +

            // Chat window
            '<div id="chatbot-window">' +

                // Header
                '<div id="chatbot-header">' +
                    '<div class="chatbot-title">' +
                        '<span class="header-icon">&#129302;</span>' +
                        '<span>Blog Q&amp;A Assistant</span>' +
                    '</div>' +
                    '<button class="chatbot-close" title="Close">&times;</button>' +
                '</div>' +

                // Strategy selector
                '<div id="chatbot-strategy-bar">' +
                    '<label for="chatbot-strategy-select">Strategy:</label>' +
                    '<select id="chatbot-strategy-select">' +
                        '<option value="">Loading...</option>' +
                    '</select>' +
                '</div>' +

                // Messages
                '<div id="chatbot-messages">' +
                    '<div class="chatbot-welcome">' +
                        '<span class="welcome-icon">&#128218;</span>' +
                        '<strong>Welcome!</strong><br>' +
                        'Ask me anything about this blog\'s content.<br>' +
                        'I\'ll answer based on published posts.' +
                        '<div class="chatbot-suggestions">' +
                            '<button class="chatbot-suggestion-btn">What topics are covered?</button>' +
                            '<button class="chatbot-suggestion-btn">What was the latest post about?</button>' +
                            '<button class="chatbot-suggestion-btn">Summarize this blog</button>' +
                        '</div>' +
                    '</div>' +
                '</div>' +

                // Input
                '<div id="chatbot-input-area">' +
                    '<textarea id="chatbot-input" placeholder="Ask a question..." rows="1"></textarea>' +
                    '<button id="chatbot-send-btn" title="Send">&#10148;</button>' +
                '</div>' +
            '</div>';

        var container = document.createElement('div');
        container.id = 'chatbot-container';
        container.innerHTML = html;
        document.body.appendChild(container);
    }

 
    // Load available strategies from the API
    function loadStrategies() {
        var select = document.getElementById('chatbot-strategy-select');
        fetch(API_BASE, { method: 'GET' })
            .then(function (res) { return res.json(); })
            .then(function (data) {
                select.innerHTML = '';
                if (data.strategies && data.strategies.length > 0) {
                    data.strategies.forEach(function (s) {
                        var opt = document.createElement('option');
                        opt.value = s.name;
                        opt.textContent = s.displayName;
                        select.appendChild(opt);
                    });
                } else {
                    var opt = document.createElement('option');
                    opt.value = '';
                    opt.textContent = 'Default';
                    select.appendChild(opt);
                }
            })
            .catch(function () {
                select.innerHTML = '<option value="">Default</option>';
            });
    }

 
    // Event bindings
 
    function bindEvents() {
        var fab = document.getElementById('chatbot-fab');
        var win = document.getElementById('chatbot-window');
        var closeBtn = win.querySelector('.chatbot-close');
        var sendBtn = document.getElementById('chatbot-send-btn');
        var input = document.getElementById('chatbot-input');

        // Toggle window
        fab.addEventListener('click', function () {
            var isOpen = win.classList.contains('visible');
            if (isOpen) {
                win.classList.remove('visible');
                fab.classList.remove('open');
            } else {
                win.classList.add('visible');
                fab.classList.add('open');
                input.focus();
            }
        });

        closeBtn.addEventListener('click', function () {
            win.classList.remove('visible');
            fab.classList.remove('open');
        });

        // Send on click
        sendBtn.addEventListener('click', function () {
            sendMessage();
        });

        // Send on Enter (Shift+Enter for newline)
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });

        // Auto-resize textarea
        input.addEventListener('input', function () {
            this.style.height = 'auto';
            this.style.height = Math.min(this.scrollHeight, 80) + 'px';
        });

        // Suggestion buttons
        document.addEventListener('click', function (e) {
            if (e.target.classList.contains('chatbot-suggestion-btn')) {
                input.value = e.target.textContent;
                sendMessage();
            }
        });
    }

 
    // Send a message
 
    var isSending = false;

    function sendMessage() {
        var input = document.getElementById('chatbot-input');
        var question = input.value.trim();
        if (!question || isSending) return;

        // Clear welcome message on first interaction
        var welcome = document.querySelector('.chatbot-welcome');
        if (welcome) welcome.remove();

        // Display user message
        appendMessage(question, 'user');
        input.value = '';
        input.style.height = 'auto';

        // Show typing indicator
        var typing = showTyping();
        isSending = true;
        updateSendButton(true);

        var strategy = document.getElementById('chatbot-strategy-select').value;

        fetch(API_BASE, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                weblogId: weblogId,
                question: question,
                strategy: strategy || undefined,
                entryId: entryIdScope || undefined
            })
        })
        .then(function (res) { return res.json(); })
        .then(function (data) {
            removeTyping(typing);
            if (data.error) {
                appendError(data.error);
            } else {
                appendMessage(data.answer, 'bot', data);
            }
        })
        .catch(function (err) {
            removeTyping(typing);
            appendError('Network error. Please check your connection and try again.');
            console.error('[Chatbot]', err);
        })
        .finally(function () {
            isSending = false;
            updateSendButton(false);
        });
    }

 
    // DOM helpers
 
    function appendMessage(text, role, meta) {
        var container = document.getElementById('chatbot-messages');
        var div = document.createElement('div');
        div.className = 'chatbot-msg ' + role;

        if (role === 'bot') {
            // Format the answer: convert markdown-like formatting
            div.innerHTML = formatBotMessage(text);

            if (meta) {
                var metaDiv = document.createElement('div');
                metaDiv.className = 'chatbot-meta';
                metaDiv.textContent =
                    meta.strategyUsed.toUpperCase() +
                    ' · ' + meta.entriesConsulted + ' post' +
                    (meta.entriesConsulted !== 1 ? 's' : '') +
                    ' consulted · ' + (meta.latencyMs / 1000).toFixed(1) + 's';
                container.appendChild(div);
                container.appendChild(metaDiv);
                scrollToBottom();
                return;
            }
        } else {
            div.textContent = text;
        }

        container.appendChild(div);
        scrollToBottom();
    }

    function appendError(text) {
        var container = document.getElementById('chatbot-messages');
        var div = document.createElement('div');
        div.className = 'chatbot-error';
        div.textContent = text;
        container.appendChild(div);
        scrollToBottom();
    }

    function showTyping() {
        var container = document.getElementById('chatbot-messages');
        var div = document.createElement('div');
        div.className = 'chatbot-typing';
        div.innerHTML = '<span></span><span></span><span></span>';
        container.appendChild(div);
        scrollToBottom();
        return div;
    }

    function removeTyping(el) {
        if (el && el.parentNode) {
            el.parentNode.removeChild(el);
        }
    }

    function updateSendButton(disabled) {
        var btn = document.getElementById('chatbot-send-btn');
        btn.disabled = disabled;
    }

    function scrollToBottom() {
        var container = document.getElementById('chatbot-messages');
        setTimeout(function () {
            container.scrollTop = container.scrollHeight;
        }, 50);
    }

    /**
     * Light formatting for bot responses:
     * - **bold** → <strong>
     * - Newlines → <br>
     * - "Title" in quotes → highlighted
     */
    function formatBotMessage(text) {
        // Escape HTML entities first for security
        var escaped = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');

        // Convert markdown bold
        escaped = escaped.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

        // Convert bullet points
        escaped = escaped.replace(/^[-•]\s+/gm, '&#8226; ');

        // Convert markdown italics
        escaped = escaped.replace(/\*(.+?)\*/g, '<em>$1</em>');

        // Convert newlines to <br>
        escaped = escaped.replace(/\n/g, '<br>');

        return escaped;
    }
 
    // Auto-initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
