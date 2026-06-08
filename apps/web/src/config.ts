export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/** Fallback auth base: in the split deployment auth and document REST are different services. */
export const AUTH_API_BASE = import.meta.env.VITE_AUTH_API_BASE_URL ?? API_BASE;

export const WS_BASE = import.meta.env.VITE_WS_BASE_URL ?? websocketBaseFromApiBase(API_BASE);

export function websocketBaseFromApiBase(apiBase: string): string {
  const url = new URL(apiBase);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return url.toString().replace(/\/+$/, '');
}
