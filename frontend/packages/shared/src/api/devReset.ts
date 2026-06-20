import axios from 'axios';
import { apiClient } from './client';
import { extractApiErrorMessage } from './extractApiErrorMessage';

export interface DevResetStatusResponse {
  devResetEnabled: boolean;
  profile: string;
}

export interface ResetPlpDemoDataResult {
  status: 'success';
}

const RESET_STEPS = [
  { key: 'lending', label: 'lending data', path: '/api/v1/dev/reset-lending-data' },
  { key: 'program', label: 'program data', path: '/api/v1/dev/reset-program-data' },
  { key: 'users', label: 'portal users', path: '/api/v1/dev/reset-users' },
] as const;

export async function getDevResetStatus(): Promise<DevResetStatusResponse> {
  const { data } = await apiClient.get<DevResetStatusResponse>('/api/v1/dev/status');
  return data;
}

export async function resetPlpDemoData(): Promise<ResetPlpDemoDataResult> {
  for (const step of RESET_STEPS) {
    try {
      await apiClient.post(step.path);
    } catch (err) {
      const detail = extractApiErrorMessage(err, `Could not reset ${step.label}`);
      throw new Error(`Failed to reset ${step.label}: ${detail}`);
    }
  }
  return { status: 'success' };
}

export function isDevResetDisabledError(err: unknown): boolean {
  if (!axios.isAxiosError(err)) return false;
  const status = err.response?.status;
  const message =
    typeof err.response?.data === 'object' &&
    err.response?.data !== null &&
    'message' in err.response.data
      ? String((err.response.data as { message?: unknown }).message)
      : '';
  return status === 403 && message.toLowerCase().includes('dev reset is disabled');
}
