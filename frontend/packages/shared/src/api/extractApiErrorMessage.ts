import axios from 'axios'

function nonEmptyString(v: unknown): string | null {
  if (typeof v !== 'string') return null
  const t = v.trim()
  return t.length > 0 ? t : null
}

function looksTechnical(message: string): boolean {
  const m = message.trim()
  if (!m) return true
  if (m.startsWith('{') || m.startsWith('[')) return true
  if (/^Request failed$/i.test(m)) return true
  return false
}

const HTTP_FALLBACK: Record<number, string> = {
  400: 'Please check your entries and try again.',
  403: 'You do not have permission to perform this action.',
  404: 'The requested item was not found.',
  409: 'This record conflicts with existing data.',
  422: 'We could not complete this action. Please review the details.',
  500: 'Something went wrong. Please try again shortly.',
};

/**
 * Human-readable message from an axios/API failure without producing technical details.
 */
export function extractApiErrorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const raw = err.response?.data;
    if (raw != null && typeof raw === 'object' && !Array.isArray(raw)) {
      const data = raw as Record<string, unknown>;
      const fromMessage = nonEmptyString(data.message);
      if (fromMessage && !looksTechnical(fromMessage)) return fromMessage;
      const fromDetail = nonEmptyString(data.detail);
      if (fromDetail && !looksTechnical(fromDetail)) return fromDetail;
      const fromError = nonEmptyString(data.error);
      if (fromError && !looksTechnical(fromError)) return fromError;
    }
    const status = err.response?.status;
    if (status != null && HTTP_FALLBACK[status]) return HTTP_FALLBACK[status];
  }
  if (err instanceof Error) {
    const m = nonEmptyString(err.message);
    if (m && !looksTechnical(m)) return m;
  }
  return fallback;
}
