import axios, { type AxiosResponse } from 'axios';
import {
  anchorAccessHeaders,
  borrowerAccessHeaders,
  getStoredAuthUser,
  invoiceAccessHeaders,
  lenderLoanActionHeaders,
  loanRepayHeaders,
} from '../auth/lenderLoanHeaders';

function trimNonEmpty(v: unknown): string | undefined {
  if (v === undefined || v === null) return undefined;
  const s = String(v).trim();
  return s.length > 0 ? s : undefined;
}

/** Runtime/build API prefix. Paths already include `/api/v1/...`; do not default to `/api` (that double-prefixes). */
function resolveApiBaseUrl(): string {
  if (typeof window !== 'undefined' && window.__ENV__ && 'VITE_API_BASE_URL' in window.__ENV__) {
    const v = window.__ENV__.VITE_API_BASE_URL;
    if (v !== undefined && v !== null) return String(v).trim();
  }
  return trimNonEmpty(import.meta.env?.VITE_API_BASE_URL as string | undefined) ?? '';
}

const API_BASE_URL = resolveApiBaseUrl();

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem((window.__PLP_TOKEN_KEY__ ?? 'plp_access_token'));
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  const user = getStoredAuthUser();
  if (user?.role === 'BORROWER') {
    Object.assign(config.headers, borrowerAccessHeaders());
  } else if (user?.linkedEntityType === 'ANCHOR') {
    Object.assign(config.headers, anchorAccessHeaders());
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem((window.__PLP_TOKEN_KEY__ ?? 'plp_access_token'));
      localStorage.removeItem((window.__PLP_REFRESH_KEY__ ?? 'plp_refresh_token'));
      localStorage.removeItem((window.__PLP_USER_KEY__ ?? 'plp_user'));
      const base = (import.meta.env?.BASE_URL as string | undefined) ?? '/';
      const loginPath = `${base.replace(/\/?$/, '')}/login`;
      window.location.href = loginPath.startsWith('/') ? loginPath : `/${loginPath}`;
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  login: (email: string, password: string) =>
    apiClient.post('/api/v1/auth/login', { email, password }),
  register: (data: Record<string, unknown>) =>
    apiClient.post('/api/v1/auth/register', data, { headers: lenderLoanActionHeaders() }),
};

export const userApi = {
  list: (params?: { role?: string; linkedEntityType?: string }) =>
    apiClient.get<{ status?: string; data?: unknown[] }>('/api/v1/users', { params }),
};

export const programApi = {
  list: () => apiClient.get('/api/v1/programs'),
  get: (id: string) => apiClient.get(`/api/v1/programs/${id}`),
  create: (data: Record<string, unknown>) =>
    apiClient.post('/api/v1/programs', data, { headers: lenderLoanActionHeaders() }),
  update: (id: string, data: Record<string, unknown>) =>
    apiClient.put(`/api/v1/programs/${id}`, data, { headers: lenderLoanActionHeaders() }),
  updateStatus: (id: string, status: string) =>
    apiClient.patch(`/api/v1/programs/${id}/status`, { status }, { headers: lenderLoanActionHeaders() }),
  getUtilization: (id: string) => apiClient.get(`/api/v1/programs/${id}/utilization`),
};

export const subProgramApi = {
  list: () => apiClient.get('/api/v1/sub-programs'),
  listByProgram: (programId: string) =>
    apiClient.get(`/api/v1/programs/${programId}/sub-programs`),
  create: (payload: Record<string, unknown>) =>
    apiClient.post('/api/v1/sub-programs', payload, { headers: lenderLoanActionHeaders() }),
  approve: (id: string) =>
    apiClient.post(`/api/v1/sub-programs/${id}/approve`, {}, { headers: lenderLoanActionHeaders() }),
  deactivate: (id: string) =>
    apiClient.post(`/api/v1/sub-programs/${id}/deactivate`, {}, { headers: lenderLoanActionHeaders() }),
  listBorrowers: (subProgramId: string) =>
    apiClient.get(`/api/v1/sub-programs/${subProgramId}/borrowers`),
  addBorrower: (subProgramId: string, payload: Record<string, unknown>) =>
    apiClient.post(`/api/v1/sub-programs/${subProgramId}/borrowers`, payload, {
      headers: lenderLoanActionHeaders(),
    }),
};

export const anchorApi = {
  list: () => apiClient.get('/api/v1/anchors'),
  get: (id: string) => apiClient.get(`/api/v1/anchors/${id}`),
  create: (data: Record<string, unknown>) =>
    apiClient.post('/api/v1/anchors', data, { headers: lenderLoanActionHeaders() }),
  update: (id: string, data: Record<string, unknown>) =>
    apiClient.put(`/api/v1/anchors/${id}`, data, { headers: lenderLoanActionHeaders() }),
  updateStatus: (id: string, status: string) =>
    apiClient.patch(`/api/v1/anchors/${id}/status`, { status }, { headers: lenderLoanActionHeaders() }),
};

export const borrowerApi = {
  list: (params?: Record<string, string>) => apiClient.get('/api/v1/borrowers', { params }),
  create: (payload: Record<string, unknown>) =>
    apiClient.post('/api/v1/borrowers', payload, { headers: lenderLoanActionHeaders() }),
  get: (id: string) => apiClient.get(`/api/v1/borrowers/${id}`),
  getLimits: (id: string) => apiClient.get(`/api/v1/borrowers/${id}/limits`),
  updateStatus: (id: string, status: string) =>
    apiClient.patch(`/api/v1/borrowers/${id}/status`, { status }, { headers: lenderLoanActionHeaders() }),
};

export const invoiceApi = {
  borrowerAccept: (invoiceId: string, borrowerId: string) =>
    apiClient.post(`/api/v1/invoices/${invoiceId}/borrower-accept`, null, {
      params: { borrowerId },
    }),
  downloadDigitalInvoice: (invoiceId: string) =>
    apiClient.get<Blob>(`/api/v1/invoices/${invoiceId}/digital-invoice/download`, {
      responseType: 'blob',
      headers: invoiceAccessHeaders(),
    }),
};

function parseFilenameFromContentDisposition(cd: string | undefined): string | null {
  if (!cd) return null;
  const star = /filename\*=(?:UTF-8'')?([^;\n]+)/i.exec(cd);
  if (star) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"(.*)"$/, '$1'));
    } catch {
      return star[1].trim().replace(/"/g, '');
    }
  }
  const quoted = /filename="([^"]+)"/i.exec(cd);
  if (quoted) return quoted[1];
  const plain = /filename=([^;\n]+)/i.exec(cd);
  return plain ? plain[1].trim().replace(/"/g, '') : null;
}

function openInvoiceBlobResponse(res: AxiosResponse<Blob>): void {
  const blob = res.data;
  const cd = res.headers['content-disposition'];
  const parsedName = parseFilenameFromContentDisposition(
    typeof cd === 'string' ? cd : Array.isArray(cd) ? cd[0] : undefined,
  );
  const filename = parsedName || 'digital-invoice';
  const url = URL.createObjectURL(blob);
  const ctHeader = res.headers['content-type'];
  const ct =
    (typeof ctHeader === 'string' ? ctHeader : Array.isArray(ctHeader) ? ctHeader[0] : '') ||
    blob.type ||
    '';
  const isPdfOrImage = ct.includes('application/pdf') || ct.startsWith('image/');
  try {
    if (isPdfOrImage) {
      const w = window.open(url, '_blank', 'noopener,noreferrer');
      if (w) {
        setTimeout(() => URL.revokeObjectURL(url), 120_000);
        return;
      }
    }
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.rel = 'noopener noreferrer';
    document.body.appendChild(a);
    a.click();
    a.remove();
  } finally {
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }
}

/** Fetches the digital invoice via authenticated API and opens inline (PDF/image) or downloads. */
export async function openDigitalInvoiceDownload(invoiceId: string): Promise<void> {
  try {
    const res = await invoiceApi.downloadDigitalInvoice(invoiceId);
    openInvoiceBlobResponse(res);
  } catch (err: unknown) {
    if (axios.isAxiosError(err) && err.response?.data instanceof Blob) {
      const text = await err.response.data.text();
      try {
        const j = JSON.parse(text) as { message?: string };
        throw new Error(j.message || 'Digital invoice file not available');
      } catch (parseErr: unknown) {
        if (parseErr instanceof SyntaxError) {
          throw new Error(text.trim().slice(0, 280) || 'Digital invoice file not available');
        }
        throw parseErr;
      }
    }
    throw err;
  }
}

export const loanApi = {
  list: (params?: Record<string, string>) => apiClient.get('/api/v1/loans', { params }),
  get: (id: string) => apiClient.get(`/api/v1/loans/${id}`),
  request: (data: Record<string, unknown>) => apiClient.post('/api/v1/loans', data),
  approve: (id: string, data?: Record<string, unknown>) =>
    apiClient.post(`/api/v1/loans/${id}/approve`, data ?? {}, { headers: lenderLoanActionHeaders() }),
  reject: (id: string, reason: string) =>
    apiClient.post(`/api/v1/loans/${id}/reject`, { reason }, { headers: lenderLoanActionHeaders() }),
  initiateDisbursement: (id: string, amount: number) =>
    apiClient.post(
      `/api/v1/loans/${id}/initiate-disbursement`,
      { amount },
      { headers: lenderLoanActionHeaders() },
    ),
  disburse: (id: string, amount: number) =>
    apiClient.post(`/api/v1/loans/${id}/disburse`, { amount }, { headers: lenderLoanActionHeaders() }),
  cancelDisbursement: (id: string) =>
    apiClient.post(`/api/v1/loans/${id}/cancel-disbursement`, {}, { headers: lenderLoanActionHeaders() }),
  repay: (id: string, amount: number) =>
    apiClient.post(`/api/v1/loans/${id}/repay`, { amount }, { headers: loanRepayHeaders() }),
};

export const salaryApi = {
  upload: (anchorId: string, programId: string, payPeriod: string, file: File) => {
    const formData = new FormData();
    formData.append('anchorId', anchorId);
    formData.append('programId', programId);
    formData.append('payPeriod', payPeriod);
    formData.append('file', file);
    return apiClient.post('/api/v1/salary/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  create: (data: Record<string, unknown>) => apiClient.post('/api/v1/salary', data),
  list: (params: Record<string, string>) => apiClient.get('/api/v1/salary', { params }),
  getLatest: (borrowerId: string) => apiClient.get(`/api/v1/salary/borrower/${borrowerId}/latest`),
};

export const eligibilityApi = {
  check: (borrowerId: string, programId: string, requestedAmount: number) =>
    apiClient.get('/api/v1/loans/eligibility', { params: { borrowerId, programId, requestedAmount } }),
};

export const portalApi = {
  anchorDashboard: () => apiClient.get('/api/v1/portal/anchor/dashboard'),
  anchorPrograms: (anchorId: string) => apiClient.get('/api/v1/portal/anchor/programs', { params: { anchorId } }),
  anchorEmployees: (anchorId: string, programId?: string) =>
    apiClient.get('/api/v1/portal/anchor/employees', { params: { anchorId, ...(programId ? { programId } : {}) } }),
  anchorSalary: (anchorId: string, payPeriod?: string) =>
    apiClient.get('/api/v1/portal/anchor/salary', {
      params: { anchorId, ...(payPeriod ? { payPeriod } : {}) },
    }),
  anchorSalaryUpload: (anchorId: string, programId: string, payPeriod: string, file: File) => {
    const formData = new FormData();
    formData.append('anchorId', anchorId);
    formData.append('programId', programId);
    formData.append('payPeriod', payPeriod);
    formData.append('file', file);
    return apiClient.post('/api/v1/portal/anchor/salary/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  borrowerDashboard: () => apiClient.get('/api/v1/portal/borrower/dashboard'),
  borrowerLoans: (borrowerId: string) => apiClient.get('/api/v1/portal/borrower/loans', { params: { borrowerId } }),
  borrowerEligibility: (borrowerId: string, programId: string, requestedAmount: number, salaryDataId?: string) =>
    apiClient.get('/api/v1/portal/borrower/eligibility', {
      params: {
        borrowerId,
        programId,
        requestedAmount,
        ...(salaryDataId ? { salaryDataId } : {}),
      },
    }),
  borrowerInvoiceEligibility: (borrowerId: string, programId: string, invoiceId: string, requestedAmount: number) =>
    apiClient.get('/api/v1/portal/borrower/invoice-eligibility', { params: { borrowerId, programId, invoiceId, requestedAmount } }),
  borrowerRequestLoan: (data: Record<string, unknown>) => apiClient.post('/api/v1/portal/borrower/loans/request', data),
  anchorInvoices: (anchorId: string, programId?: string) =>
    apiClient.get('/api/v1/portal/anchor/invoices', { params: { anchorId, ...(programId ? { programId } : {}) } }),
  anchorCreateInvoice: (data: Record<string, unknown>) =>
    apiClient.post('/api/v1/portal/anchor/invoices', data),
  anchorUploadDigitalInvoice: (invoiceId: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient.post(`/api/v1/portal/anchor/invoices/${invoiceId}/digital-invoice`, formData);
  },
  anchorInvoiceUpload: (anchorId: string, programId: string, file: File) => {
    const formData = new FormData();
    formData.append('anchorId', anchorId);
    formData.append('programId', programId);
    formData.append('file', file);
    return apiClient.post('/api/v1/portal/anchor/invoices/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  anchorVerifyInvoice: (invoiceId: string) =>
    apiClient.post(`/api/v1/portal/anchor/invoices/${invoiceId}/verify`),
  anchorConfirmInvoice: (invoiceId: string) =>
    apiClient.post(`/api/v1/portal/anchor/invoices/${invoiceId}/confirm`),
};

export const integrationApi = {
  getSalaryInfo: (employeeId: string, anchorId: string) =>
    apiClient.get('/api/v1/integrations/hr/salary', { params: { employeeId, anchorId } }),
  getEarnedSalary: (employeeId: string, anchorId: string) =>
    apiClient.get('/api/v1/integrations/hr/earned-salary', { params: { employeeId, anchorId } }),
  getInvoice: (invoiceNumber: string, anchorId: string) =>
    apiClient.get(`/api/v1/integrations/erp/invoice/${invoiceNumber}`, { params: { anchorId } }),
  listInvoices: (buyerId: string, anchorId: string) =>
    apiClient.get('/api/v1/integrations/erp/invoices', { params: { buyerId, anchorId } }),
};

export const notificationApi = {
  list: (recipientId: string, page = 0, size = 20) =>
    apiClient.get('/api/v1/notifications', { params: { recipientId, page, size } }),
  unreadCount: (recipientId: string) =>
    apiClient.get('/api/v1/notifications/unread-count', { params: { recipientId } }),
  templates: () => apiClient.get('/api/v1/notifications/templates'),
  updateTemplate: (id: string, data: Record<string, string>) =>
    apiClient.put(`/api/v1/notifications/templates/${id}`, data),
};

export const reportApi = {
  disbursementSummary: (fromDate?: string, toDate?: string) =>
    apiClient.get('/api/v1/reports/disbursement-summary', { params: { fromDate, toDate } }),
  portfolioSummary: () => apiClient.get('/api/v1/reports/portfolio-summary'),
  overdueReport: () => apiClient.get('/api/v1/reports/overdue'),
  dashboardStats: () => apiClient.get('/api/v1/reports/dashboard-stats'),
  definitions: () => apiClient.get('/api/v1/reports/definitions'),
  auditEvents: (page = 0, size = 50) =>
    apiClient.get('/api/v1/reports/audit', { params: { page, size } }),
  auditEventsForEntity: (entityType: string, entityId: string, page = 0) =>
    apiClient.get(`/api/v1/reports/audit/${entityType}/${entityId}`, { params: { page } }),
  exportDisbursement: (fromDate?: string, toDate?: string) =>
    apiClient.get('/api/v1/reports/export/disbursement-summary', {
      params: { fromDate, toDate }, responseType: 'blob',
    }),
  exportPortfolio: () =>
    apiClient.get('/api/v1/reports/export/portfolio-summary', { responseType: 'blob' }),
  exportOverdue: () =>
    apiClient.get('/api/v1/reports/export/overdue', { responseType: 'blob' }),
};

/** Centralized audit events from program-service and lending-service (lender portal). */
export interface AuditEventRow {
  id: string;
  eventType: string;
  entityType: string;
  entityId: string | null;
  action: string;
  performedByUserId: string | null;
  performedByRole: string | null;
  linkedEntityId: string | null;
  linkedEntityType: string | null;
  status: string;
  message: string | null;
  createdAt: string;
}

export interface AuditEventsPageBody {
  content: AuditEventRow[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first?: boolean;
  last?: boolean;
}

export interface AuditListParams {
  eventType?: string;
  entityType?: string;
  entityId?: string;
  status?: string;
  performedByRole?: string;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const auditApi = {
  listProgramAudit: (params?: AuditListParams) =>
    apiClient.get<{ status?: string; data?: AuditEventsPageBody }>('/api/v1/program-audit/events', {
      params,
      headers: lenderLoanActionHeaders(),
    }),
  listLendingAudit: (params?: AuditListParams) =>
    apiClient.get<{ status?: string; data?: AuditEventsPageBody }>('/api/v1/lending-audit/events', {
      params,
      headers: lenderLoanActionHeaders(),
    }),
};

export const kfsApi = {
  getKfs: (loanId: string) =>
    apiClient.get(`/api/v1/loans/${loanId}/kfs`, { responseType: 'text' }),
};
