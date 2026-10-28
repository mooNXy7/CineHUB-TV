/* CineHUB telemetry client — opt-in, privacy-first, best-effort only. */
(() => {
  'use strict';

  const PREFERENCE_KEY = 'cinehub.telemetry.enabled';
  const SUPABASE_URL = 'https://qdheeiitzlvynshvkwcs.supabase.co';
  const SUPABASE_PUBLISHABLE_KEY = 'sb_publishable_AzpaAW6_qoeiBxy4Pywa4g_XDn2up0z';
  const ENDPOINT = SUPABASE_URL + '/functions/v1/cinehub-telemetry';
  const allowedEvents = new Set([
    'app_open', 'session_start', 'heartbeat', 'session_end',
    'app_error', 'playback_start', 'playback_error', 'playback_success'
  ]);

  const uuid = () => {
    try {
      if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
    } catch (_) {}
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
      const r = Math.random() * 16 | 0;
      return (c === 'x' ? r : (r & 3 | 8)).toString(16);
    });
  };
  const storageGet = (storage, key) => { try { return storage.getItem(key); } catch (_) { return null; } };
  const storageSet = (storage, key, value) => { try { storage.setItem(key, value); return true; } catch (_) { return false; } };
  const getOrCreate = (storage, key) => {
    const current = storageGet(storage, key);
    if (current) return current;
    const created = uuid();
    storageSet(storage, key, created);
    return created;
  };

  const ua = (() => { try { return navigator.userAgent || ''; } catch (_) { return ''; } })();
  const isTV = (() => { try { return location.protocol === 'file:' || /CineHUB-TV/i.test(ua); } catch (_) { return /CineHUB-TV/i.test(ua); } })();
  const isMobile = /CineHUB-Mobile/i.test(ua);
  const appId = isTV ? 'cinehub-tv' : (isMobile ? 'cinehub-mobile' : 'cinehub-web');
  const platform = isTV ? 'android_tv' : (isMobile ? 'android_mobile' : 'web');
  const versionMatch = ua.match(/CineHUB-(?:Mobile|TV)\/([0-9.]+)/i);
  const appVersion = versionMatch?.[1] || 'unknown';

  let enabled = storageGet(window.localStorage, PREFERENCE_KEY) === 'true';
  let heartbeat = null;
  let installationId = null;
  let sessionId = null;

  const safeProperties = (event, properties) => {
    if (event === 'app_error') {
      const code = properties && typeof properties.error_code === 'string' ? properties.error_code : 'UNHANDLED_ERROR';
      return { error_code: /^[A-Z0-9_]{1,48}$/.test(code) ? code : 'UNHANDLED_ERROR' };
    }
    return {};
  };
  const send = (event, properties = {}) => {
    if (!enabled || !allowedEvents.has(event)) return;
    if (!installationId) installationId = getOrCreate(window.localStorage, 'cinehub_telemetry_installation_id');
    if (!sessionId) sessionId = getOrCreate(window.sessionStorage, 'cinehub_telemetry_session_id');
    const payload = {
      event_id: uuid(), event, app_id: appId, platform, app_version: appVersion,
      installation_id: installationId, session_id: sessionId,
      properties: safeProperties(event, properties)
    };
    try {
      const request = window.fetch(ENDPOINT, {
        method: 'POST',
        headers: { apikey: SUPABASE_PUBLISHABLE_KEY, 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        keepalive: event === 'session_end'
      });
      if (request && typeof request.catch === 'function') request.catch(() => {});
    } catch (_) { /* Telemetry must never affect playback, startup or navigation. */ }
  };
  const stopHeartbeat = () => {
    if (heartbeat !== null) {
      try { window.clearInterval(heartbeat); } catch (_) {}
      heartbeat = null;
    }
  };
  const start = () => {
    if (!enabled) return;
    send('app_open');
    send('session_start');
    if (heartbeat === null) {
      heartbeat = window.setInterval(() => {
        let visible = true;
        try { visible = document.visibilityState === 'visible'; } catch (_) {}
        if (visible) send('heartbeat');
      }, 5 * 60 * 1000);
    }
  };

  window.CineHUBTelemetry = {
    setEnabled(value) {
      enabled = value === true;
      storageSet(window.localStorage, PREFERENCE_KEY, enabled ? 'true' : 'false');
      if (enabled) start();
      else stopHeartbeat();
    },
    isEnabled() { return enabled; },
    track(event, properties = {}) { send(event, properties); }
  };

  if (enabled) start();
  window.addEventListener('pagehide', () => {
    if (enabled) send('session_end');
    stopHeartbeat();
  }, { once: true });
  window.addEventListener('error', () => send('app_error', { error_code: 'UNHANDLED_ERROR' }));
  window.addEventListener('unhandledrejection', () => send('app_error', { error_code: 'UNHANDLED_REJECTION' }));
})();