/** Small display helpers shared by the dashboards and admin tables. */

/** "just now", "12 min ago", "3 h ago", "5 days ago", then the date. */
export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return '—';
  const then = new Date(iso).getTime();
  const minutes = Math.round((Date.now() - then) / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  if (days < 14) return `${days} day${days === 1 ? '' : 's'} ago`;
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }).format(then);
}

/** "in 12 min", "in 3 h", "in 2 days". For times in the future; a past one reads "now". */
export function timeUntil(iso: string | null | undefined): string {
  if (!iso) return '—';
  const minutes = Math.round((new Date(iso).getTime() - Date.now()) / 60_000);
  if (minutes < 1) return 'now';
  if (minutes < 60) return `in ${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `in ${hours} h`;
  return `in ${Math.round(hours / 24)} days`;
}

/** The backend's ProblemDetail message, which is written for the person reading it. */
export function apiError(err: unknown, fallback: string): string {
  const e = err as { error?: { detail?: string }; status?: number };
  if (e?.error?.detail) return e.error.detail;
  if (e?.status === 0) return 'Cannot reach the API. Is the backend running on :8080?';
  return fallback;
}

/** 1200 → "1.2s", 95000 → "1m 35s". */
export function duration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return '—';
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const m = Math.floor(ms / 60000);
  return `${m}m ${Math.round((ms % 60000) / 1000)}s`;
}
