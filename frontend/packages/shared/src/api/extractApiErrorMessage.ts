import axios from 'axios';

function nonEmptyString(v: unknown): string | null {
  if (typeof v !== 'string') return null;
  const t = v.trim();
  return t.length > 0 ? t : null;
}

/**
 * Human-readable message from an axios/API failure without producing "undefined".
 */
export function extractApiErrorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const raw = err.response?.data;
    if (raw != null && typeof raw === 'object' && !Array.isArray(raw)) {
      const data = raw as Record<string, unknown>;
      const fromMessage = nonEmptyString(data.message);
      if (fromMessage) return fromMessage;
      const fromError = nonEmptyString(data.error);
      if (fromError) return fromError;
    }
    const status = err.response?.status;
    if (status != null) {
      const statusText = nonEmptyString(err.response?.statusText);
      return statusText ? `${status} ${statusText}` : String(status);
    }
  }
  if (err instanceof Error) {
    const m = nonEmptyString(err.message);
    if (m) return m;
  }
  return fallback;
}
