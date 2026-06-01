/**
 * Spring SaaS Support AI — Embeddable Chat Widget
 *
 * Usage:
 *   <script src="https://your-domain/widget.js" data-widget-id="YOUR_CHATBOT_UUID"></script>
 *
 * No external dependencies. Pure Vanilla JS. Shadow DOM isolates all styles.
 */
(function () {
  'use strict';

  function generateUuid() {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    // Fallback for non-secure contexts (file://, plain HTTP)
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      var r = Math.random() * 16 | 0;
      return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
  }

  var _ws_debug = false;

  function _log() {
    if (_ws_debug) {
      console.log.apply(console, ['[widget]'].concat(Array.prototype.slice.call(arguments)));
    }
  }

  // ---------------------------------------------------------------------------
  // Bootstrap
  // ---------------------------------------------------------------------------

  var scriptTag = document.currentScript || document.querySelector('script[data-widget-id]');
  if (!scriptTag) return;

  // Derive base URL from the script's own src so API calls work from any origin
  // e.g. script src="http://localhost:8081/widget.js" → apiBase="http://localhost:8081"
  var _scriptSrc = scriptTag.src || '';
  var apiBase = _scriptSrc ? _scriptSrc.substring(0, _scriptSrc.lastIndexOf('/')) : '';

  var chatbotId = scriptTag.getAttribute('data-widget-id');
  if (!chatbotId || !chatbotId.trim()) return;
  chatbotId = chatbotId.trim();

  var visitorId = localStorage.getItem('_ws_visitor_id');
  if (!visitorId) {
    visitorId = generateUuid();
    localStorage.setItem('_ws_visitor_id', visitorId);
  }

  var sessionId = sessionStorage.getItem('_ws_session_id');
  if (!sessionId) {
    sessionId = generateUuid();
    sessionStorage.setItem('_ws_session_id', sessionId);
  }

  _log('init chatbotId=' + chatbotId + ' sessionId=' + sessionId);

  var DEFAULTS = {
    botName: 'Support Bot',
    brandColor: '#F59E0B',
    welcomeMessage: 'Hello! How can I help you?',
  };

  fetchConfig(chatbotId).then(function (config) {
    mountWidget(chatbotId, sessionId, config);
  }).catch(_log);

  // ---------------------------------------------------------------------------
  // Config fetch
  // ---------------------------------------------------------------------------

  function fetchConfig(id) {
    return fetch(apiBase + '/api/v1/widget/' + id + '/config')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (json) {
        var d = (json && json.data) || {};
        return {
          botName: d.name || DEFAULTS.botName,
          brandColor: d.primaryColor || DEFAULTS.brandColor,
          welcomeMessage: d.welcomeMessage || DEFAULTS.welcomeMessage,
        };
      })
      .catch(function (err) {
        _log('config fetch failed, using defaults', err);
        return Object.assign({}, DEFAULTS);
      });
  }

  // ---------------------------------------------------------------------------
  // Mount — Shadow DOM
  // ---------------------------------------------------------------------------

  function mountWidget(chatbotId, sessionId, config) {
    if (document.getElementById('_ws_root')) return; // already mounted
    var host = document.createElement('div');
    host.id = '_ws_root';
    document.body.appendChild(host);
    var shadow = host.attachShadow({ mode: 'open' });

    injectStyles(shadow, config.brandColor);

    var bubble = buildBubble(shadow);
    var tooltip = buildTooltip(shadow, config.welcomeMessage);
    var drawer = buildDrawer(shadow, config);

    var drawerOpen = false;
    var tooltipDismissed = false;
    var streaming = false;
    var activeController = null; // AbortController for the in-flight fetch

    // Show proactive greeting after 3 s if drawer stays closed
    var greetingTimer = setTimeout(function () {
      if (!drawerOpen && !tooltipDismissed) {
        tooltip.style.display = 'block';
      }
    }, 3000);

    bubble.addEventListener('click', function () {
      clearTimeout(greetingTimer);
      drawerOpen = !drawerOpen;
      drawer.style.display = drawerOpen ? 'flex' : 'none';
      tooltip.style.display = 'none';
      tooltipDismissed = true;
      if (drawerOpen) {
        scrollToBottom(shadow);
      }
    });

    shadow.querySelector('#_ws_close').addEventListener('click', function () {
      drawerOpen = false;
      drawer.style.display = 'none';
      if (activeController) {
        activeController.abort();
        activeController = null;
      }
    });

    shadow.querySelector('#_ws_tooltip-close').addEventListener('click', function (e) {
      e.stopPropagation();
      tooltip.style.display = 'none';
      tooltipDismissed = true;
    });

    shadow.querySelector('#_ws_send').addEventListener('click', function () {
      if (!streaming) handleSend();
    });

    shadow.querySelector('#_ws_input').addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && !e.shiftKey && !streaming) {
        e.preventDefault();
        handleSend();
      }
    });

    function handleSend() {
      var input = shadow.querySelector('#_ws_input');
      var query = input.value.trim();
      if (!query) return;

      // Abort any in-flight request before starting a new one
      if (activeController) {
        activeController.abort();
      }
      activeController = new AbortController();

      input.value = '';
      appendUserMessage(shadow, query);
      setStreamingState(shadow, true);
      streaming = true;

      sendMessage(chatbotId, sessionId, query, shadow, activeController.signal)
        .catch(function (err) {
          if (err && err.name === 'AbortError') {
            _log('send aborted');
            return;
          }
          _log('send error', err);
          appendErrorMessage(shadow);
        })
        .finally(function () {
          streaming = false;
          activeController = null;
          setStreamingState(shadow, false);
        });
    }
  }

  // ---------------------------------------------------------------------------
  // DOM builders
  // ---------------------------------------------------------------------------

  function buildBubble(shadow) {
    var el = document.createElement('button');
    el.id = '_ws_bubble';
    el.setAttribute('aria-label', 'Open support chat');
    el.innerHTML = chatIconSvg();
    shadow.appendChild(el);
    return el;
  }

  function buildTooltip(shadow, welcomeMessage) {
    var el = document.createElement('div');
    el.id = '_ws_tooltip';
    el.style.display = 'none';
    el.innerHTML =
      '<span id="_ws_tooltip-msg">' + escapeHtml(welcomeMessage) + '</span>' +
      '<button id="_ws_tooltip-close" aria-label="Dismiss">×</button>';
    shadow.appendChild(el);
    return el;
  }

  function buildDrawer(shadow, config) {
    var el = document.createElement('div');
    el.id = '_ws_drawer';
    el.style.display = 'none';
    el.innerHTML =
      '<div id="_ws_header">' +
        '<div id="_ws_header-info">' +
          '<span id="_ws_bot-name">' + escapeHtml(config.botName) + '</span>' +
          '<span id="_ws_status">&#9679; Online</span>' +
        '</div>' +
        '<button id="_ws_close" aria-label="Close chat">×</button>' +
      '</div>' +
      '<div id="_ws_messages"></div>' +
      '<div id="_ws_input-area">' +
        '<input id="_ws_input" type="text" placeholder="Type your message…" autocomplete="off" />' +
        '<button id="_ws_send" aria-label="Send">' + sendIconSvg() + '</button>' +
      '</div>';
    shadow.appendChild(el);

    // Seed the welcome message
    appendBotMessage(shadow, config.welcomeMessage);

    return el;
  }

  // ---------------------------------------------------------------------------
  // Message helpers
  // ---------------------------------------------------------------------------

  function appendUserMessage(shadow, text) {
    var msgs = shadow.querySelector('#_ws_messages');
    var bubble = document.createElement('div');
    bubble.className = 'msg msg-user';
    bubble.textContent = text;
    msgs.appendChild(bubble);
    scrollToBottom(shadow);
  }

  function appendBotMessage(shadow, text, sources) {
    var msgs = shadow.querySelector('#_ws_messages');
    var wrapper = document.createElement('div');
    wrapper.className = 'msg-wrapper';

    var bubble = document.createElement('div');
    bubble.className = 'msg msg-bot';
    bubble.textContent = text;
    wrapper.appendChild(bubble);

    if (sources && sources.length) {
      sources.forEach(function (src) {
        var cite = document.createElement('div');
        cite.className = 'msg-source';
        cite.textContent = '📄 ' + src;
        wrapper.appendChild(cite);
      });
    }

    msgs.appendChild(wrapper);
    scrollToBottom(shadow);
    return bubble;
  }

  function appendErrorMessage(shadow) {
    var msgs = shadow.querySelector('#_ws_messages');
    var el = document.createElement('div');
    el.className = 'msg msg-error';
    el.textContent = 'Failed to send. Please try again.';
    msgs.appendChild(el);
    scrollToBottom(shadow);
  }

  function appendStreamingBotBubble(shadow) {
    var msgs = shadow.querySelector('#_ws_messages');
    var wrapper = document.createElement('div');
    wrapper.className = 'msg-wrapper';

    var bubble = document.createElement('div');
    bubble.className = 'msg msg-bot';
    bubble.textContent = '';
    wrapper.appendChild(bubble);

    msgs.appendChild(wrapper);
    scrollToBottom(shadow);
    return bubble;
  }

  function scrollToBottom(shadow) {
    var msgs = shadow.querySelector('#_ws_messages');
    if (msgs) msgs.scrollTop = msgs.scrollHeight;
  }

  function setStreamingState(shadow, isStreaming) {
    var input = shadow.querySelector('#_ws_input');
    var btn = shadow.querySelector('#_ws_send');
    if (input) input.disabled = isStreaming;
    if (btn) btn.disabled = isStreaming;
  }

  // ---------------------------------------------------------------------------
  // Message send + SSE streaming
  // ---------------------------------------------------------------------------

  function sendMessage(chatbotId, sessionId, query, shadow, signal) {
    return fetch(apiBase + '/api/v1/widget/' + chatbotId + '/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Session-Id': sessionId,
      },
      body: JSON.stringify({ query: query }),
      signal: signal,
    }).then(function (response) {
      if (!response.ok) {
        throw new Error('HTTP ' + response.status);
      }
      return readSseStream(response.body, shadow);
    });
  }

  function readSseStream(body, shadow) {
    var reader = body.getReader();
    var decoder = new TextDecoder();
    var buffer = '';
    var pendingEventName = null; // carries "event:" value to the next "data:" line
    var botBubble = appendStreamingBotBubble(shadow);

    function pump() {
      return reader.read().then(function (result) {
        if (result.done) return;

        buffer += decoder.decode(result.value, { stream: true });
        var lines = buffer.split('\n');
        buffer = lines.pop(); // keep incomplete last line

        for (var i = 0; i < lines.length; i++) {
          var line = lines[i].trim();
          pendingEventName = processLine(line, pendingEventName, botBubble, shadow);
        }

        return pump();
      });
    }

    return pump().catch(function (err) {
      _log('stream read error', err);
      throw err;
    });
  }

  /**
   * Process one SSE line. Returns the updated pendingEventName so callers can
   * carry the event name across to the following data line.
   */
  function processLine(line, pendingEventName, botBubble, shadow) {
    if (!line) return null; // blank line = event boundary, reset event name

    if (line.startsWith('event:')) {
      // Store event name; the data line follows immediately after
      return line.slice('event:'.length).trim();
    }

    if (!line.startsWith('data:')) {
      return pendingEventName; // ignore unknown fields (id:, retry:, comments)
    }

    var dataPayload = line.slice('data:'.length).trim();
    var eventName = pendingEventName;

    var parsed = tryParseJson(dataPayload);
    if (!parsed) return null;

    if (eventName === 'error' || parsed.error) {
      _log('stream error event', parsed.message);
      botBubble.textContent = 'Failed to send. Please try again.';
      botBubble.className += ' msg-error';
      return null;
    }

    if (parsed.token !== undefined) {
      botBubble.textContent += parsed.token;
      scrollToBottom(shadow);
      return null;
    }

    if (parsed.done === true) {
      _log('stream done messageId=' + parsed.messageId);
      if (parsed.sources && parsed.sources.length > 0 && botBubble) {
        var citations = document.createElement('div');
        citations.className = '_ws_sources';
        parsed.sources.forEach(function (src) {
          var cite = document.createElement('div');
          cite.className = '_ws_cite';
          cite.textContent = '📄 ' + src.name;
          citations.appendChild(cite);
        });
        var wrapper = botBubble.parentElement;
        if (wrapper) wrapper.appendChild(citations);
      }
      return null;
    }

    return null;
  }

  // ---------------------------------------------------------------------------
  // Styles
  // ---------------------------------------------------------------------------

  function injectStyles(shadow, brandColor) {
    var style = document.createElement('style');
    style.textContent = buildCss(brandColor);
    shadow.appendChild(style);
  }

  function buildCss(brandColor) {
    return [
      ':host { --brand: ' + brandColor + '; }',

      /* Bubble */
      '#_ws_bubble {',
      '  position: fixed; bottom: 20px; right: 20px; z-index: 999999;',
      '  width: 52px; height: 52px; border-radius: 50%;',
      '  background: var(--brand); border: none; cursor: pointer;',
      '  box-shadow: 0 4px 12px rgba(0,0,0,0.25);',
      '  display: flex; align-items: center; justify-content: center;',
      '  transition: transform 0.15s;',
      '}',
      '#_ws_bubble:hover { transform: scale(1.08); }',

      /* Tooltip */
      '#_ws_tooltip {',
      '  position: fixed; bottom: 84px; right: 20px; z-index: 999998;',
      '  background: #fff; border-radius: 8px; padding: 10px 12px;',
      '  box-shadow: 0 4px 12px rgba(0,0,0,0.15);',
      '  max-width: 200px; font: 13px/1.4 system-ui, sans-serif;',
      '  color: #374151; display: flex; align-items: flex-start; gap: 6px;',
      '}',
      '#_ws_tooltip-msg { flex: 1; }',
      '#_ws_tooltip-close {',
      '  background: none; border: none; cursor: pointer;',
      '  color: #9CA3AF; font-size: 16px; line-height: 1; padding: 0;',
      '}',

      /* Drawer */
      '#_ws_drawer {',
      '  position: fixed; bottom: 84px; right: 20px; z-index: 999997;',
      '  width: 320px; height: 480px;',
      '  background: #fff; border-radius: 12px 12px 0 0;',
      '  box-shadow: 0 8px 32px rgba(0,0,0,0.18);',
      '  flex-direction: column; overflow: hidden;',
      '  animation: _ws_slide_in 0.2s ease;',
      '}',
      '@keyframes _ws_slide_in {',
      '  from { opacity: 0; transform: translateY(12px); }',
      '  to   { opacity: 1; transform: translateY(0); }',
      '}',

      /* Header */
      '#_ws_header {',
      '  background: var(--brand); padding: 14px 16px;',
      '  display: flex; align-items: center; justify-content: space-between;',
      '  flex-shrink: 0;',
      '}',
      '#_ws_header-info { display: flex; flex-direction: column; gap: 2px; }',
      '#_ws_bot-name { color: #fff; font: 600 15px/1 system-ui, sans-serif; }',
      '#_ws_status { color: rgba(255,255,255,0.85); font: 11px/1 system-ui, sans-serif; }',
      '#_ws_close {',
      '  background: none; border: none; cursor: pointer;',
      '  color: #fff; font-size: 20px; line-height: 1; padding: 0;',
      '}',

      /* Messages */
      '#_ws_messages {',
      '  flex: 1; overflow-y: auto; padding: 14px 12px;',
      '  display: flex; flex-direction: column; gap: 8px;',
      '  font: 14px/1.5 system-ui, sans-serif;',
      '}',

      /* Message bubbles */
      '.msg {',
      '  max-width: 80%; padding: 9px 12px; border-radius: 14px;',
      '  word-break: break-word; white-space: pre-wrap;',
      '}',
      '.msg-wrapper { display: flex; flex-direction: column; align-self: flex-start; max-width: 80%; }',
      '.msg-user {',
      '  background: var(--brand); color: #fff;',
      '  align-self: flex-end; border-bottom-right-radius: 4px;',
      '}',
      '.msg-bot {',
      '  background: #F3F4F6; color: #111827;',
      '  align-self: flex-start; border-bottom-left-radius: 4px;',
      '}',
      '.msg-error {',
      '  background: #FEF2F2; color: #B91C1C;',
      '  font-size: 13px; align-self: center;',
      '}',
      '.msg-source {',
      '  font-size: 11px; color: #9CA3AF;',
      '  margin-top: 3px; padding-left: 2px;',
      '}',

      /* Input area */
      '#_ws_input-area {',
      '  display: flex; gap: 8px; padding: 10px 12px;',
      '  border-top: 1px solid #E5E7EB; flex-shrink: 0;',
      '}',
      '#_ws_input {',
      '  flex: 1; border: 1px solid #D1D5DB; border-radius: 8px;',
      '  padding: 8px 10px; font: 14px system-ui, sans-serif;',
      '  outline: none; color: #111827;',
      '}',
      '#_ws_input:focus { border-color: var(--brand); }',
      '#_ws_input:disabled { opacity: 0.6; }',
      '#_ws_send {',
      '  background: var(--brand); border: none; border-radius: 8px;',
      '  width: 36px; height: 36px; cursor: pointer;',
      '  display: flex; align-items: center; justify-content: center;',
      '  flex-shrink: 0;',
      '}',
      '#_ws_send:disabled { opacity: 0.5; cursor: not-allowed; }',
      '#_ws_send:hover:not(:disabled) { filter: brightness(1.1); }',

      /* Source citations */
      '._ws_sources { padding: 4px 12px 8px; }',
      '._ws_cite { font-size: 11px; color: #6B7280; margin-top: 2px; }',
    ].join('\n');
  }

  // ---------------------------------------------------------------------------
  // SVG icons (inline, no external resources)
  // ---------------------------------------------------------------------------

  function chatIconSvg() {
    return '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>';
  }

  function sendIconSvg() {
    return '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>';
  }

  // ---------------------------------------------------------------------------
  // Utilities
  // ---------------------------------------------------------------------------

  function escapeHtml(str) {
    if (!str) return '';
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function tryParseJson(str) {
    try {
      return JSON.parse(str);
    } catch (_) {
      return null;
    }
  }
})();
