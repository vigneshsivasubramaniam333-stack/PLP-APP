import axios from 'axios';
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
  if (typeof window !== 'undefined') {
    const host = window.location.hostname.toLowerCase();
    if (host.includes('credinnov') || host.endsWith('senseitech.com')) {
      return '/plp-api';
    }
    if (host === 'localhost' || host === '127.0.0.1') {
      return 'http://localhost:8180';
    }
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
  // Let the browser set multipart boundary; default axios Content-Type is application/json.
  if (config.data instanceof FormData) {
    if (config.headers && typeof config.headers === 'object') {
      if ('delete' in config.headers && typeof config.headers.delete === 'function') {
        config.headers.delete('Content-Type');
        config.headers.delete('content-type');
      } else {
        delete (config.headers as Record<string, unknown>)['Content-Type'];
        delete (config.headers as Record<string, unknown>)['content-type'];
      }
    }
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
  changePassword: (data: { currentPassword: string; newPassword: string; confirmPassword: string }) =>
    apiClient.post('/api/v1/auth/change-password', data),
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

export const repaymentDefaultsApi = {
  list: () =>
    apiClient.get<{ status?: string; data?: import('../types').ProductRepaymentDefault[] }>(
      '/api/v1/platform/repayment-defaults',
    ),
  update: (productType: string, payload: Record<string, unknown>) =>
    apiClient.put(`/api/v1/platform/repayment-defaults/${productType}`, payload, {
      headers: lenderLoanActionHeaders(),
    }),
};

export const subProgramApi = {
  list: () => apiClient.get('/api/v1/sub-programs'),
  listByProgram: (programId: string) =>
    apiClient.get(`/api/v1/programs/${programId}/sub-programs`),
  create: (payload: Record<string, unknown>) =>
    apiClient.post('/api/v1/sub-programs', payload, { headers: lenderLoanActionHeaders() }),
  update: (id: string, payload: Record<string, unknown>) =>
    apiClient.put(`/api/v1/sub-programs/${id}`, payload, { headers: lenderLoanActionHeaders() }),
  approve: (id: string) =>
    apiClient.post(`/api/v1/sub-programs/${id}/approve`, {}, { headers: lenderLoanActionHeaders() }),
  deactivate: (id: string) =>
    apiClient.post(`/api/v1/sub-programs/${id}/deactivate`, {}, { headers: lenderLoanActionHeaders() }),
  listBorrowers: (subProgramId: string) =>
    apiClient.get(`/api/v1/sub-programs/${subProgramId}/borrowers`),
  getBorrowerLimitSummary: (subProgramId: string, borrowerId: string) =>
    apiClient.get(`/api/v1/sub-programs/${subProgramId}/borrowers/${borrowerId}/limit-summary`),
  addBorrower: (subProgramId: string, payload: Record<string, unknown>) =>
    apiClient.post(`/api/v1/sub-programs/${subProgramId}/borrowers`, payload, {
      headers: lenderLoanActionHeaders(),
    }),
  updateBorrowerTerms: (subProgramId: string, borrowerId: string, payload: Record<string, unknown>) =>
    apiClient.patch(`/api/v1/sub-programs/${subProgramId}/borrowers/${borrowerId}`, payload, {
      headers: lenderLoanActionHeaders(),
    }),
  getEffectiveBorrowerTerms: (subProgramId: string, borrowerId: string) =>
    apiClient.get(`/api/v1/sub-programs/${subProgramId}/borrowers/${borrowerId}/effective-terms`),
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
  list: (opts?: {
    search?: string;
    status?: string;
    lifecycle?: string;
    flowType?: string;
    tab?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get('/api/v1/invoices', {
      params: {
        ...(opts?.search ? { search: opts.search } : {}),
        ...(opts?.status ? { status: opts.status } : {}),
        ...(opts?.lifecycle ? { lifecycle: opts.lifecycle } : {}),
        ...(opts?.flowType ? { flowType: opts.flowType } : {}),
        ...(opts?.tab ? { tab: opts.tab } : {}),
        ...(opts?.page != null ? { page: opts.page } : {}),
        ...(opts?.size != null ? { size: opts.size } : {}),
      },
    }),
  create: (data: Record<string, unknown>) => apiClient.post('/api/v1/invoices', data),
  uploadDigitalInvoice: (invoiceId: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient.post(`/api/v1/invoices/${invoiceId}/digital-invoice`, formData);
  },
  listForBorrower: (
    borrowerId: string,
    opts?: {
      search?: string;
      status?: string;
      lifecycle?: string;
      flowType?: string;
      tab?: string;
      page?: number;
      size?: number;
    },
  ) =>
    apiClient.get(`/api/v1/invoices/borrower/${borrowerId}`, {
      params: {
        ...(opts?.search ? { search: opts.search } : {}),
        ...(opts?.status ? { status: opts.status } : {}),
        ...(opts?.lifecycle ? { lifecycle: opts.lifecycle } : {}),
        ...(opts?.flowType ? { flowType: opts.flowType } : {}),
        ...(opts?.tab ? { tab: opts.tab } : {}),
        ...(opts?.page != null ? { page: opts.page } : {}),
        ...(opts?.size != null ? { size: opts.size } : {}),
      },
    }),
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
  getPayoff: (id: string) => apiClient.get(`/api/v1/loans/${id}/payoff`),
  listRepayments: (id: string) => apiClient.get(`/api/v1/loans/${id}/repayments`),
};

export const salaryApi = {
  upload: (anchorId: string, programId: string, payPeriod: string, file: File) => {
    const formData = new FormData();
    formData.append('anchorId', anchorId);
    formData.append('programId', programId);
    formData.append('payPeriod', payPeriod);
    formData.append('file', file);
    return apiClient.post('/api/v1/salary/upload', formData);
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
    return apiClient.post('/api/v1/portal/anchor/salary/upload', formData);
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
  anchorInvoices: (
    anchorId: string,
    opts?: {
      programId?: string;
      search?: string;
      status?: string;
      lifecycle?: string;
      flowType?: string;
      tab?: string;
      page?: number;
      size?: number;
    },
  ) =>
    apiClient.get('/api/v1/portal/anchor/invoices', {
      params: {
        anchorId,
        ...(opts?.programId ? { programId: opts.programId } : {}),
        ...(opts?.search ? { search: opts.search } : {}),
        ...(opts?.status ? { status: opts.status } : {}),
        ...(opts?.lifecycle ? { lifecycle: opts.lifecycle } : {}),
        ...(opts?.flowType ? { flowType: opts.flowType } : {}),
        ...(opts?.tab ? { tab: opts.tab } : {}),
        ...(opts?.page != null ? { page: opts.page } : {}),
        ...(opts?.size != null ? { size: opts.size } : {}),
      },
    }),
  anchorCreateInvoice: (data: Record<string, unknown>) =>
    apiClient.post('/api/v1/portal/anchor/invoices', data),
  anchorUploadDigitalInvoice: (invoiceId: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient.post(`/api/v1/portal/anchor/invoices/${invoiceId}/digital-invoice`, formData);
  },
  anchorInvoiceUpload: (anchorId: string, programId: string, file: File, subProgramId?: string) => {
    const formData = new FormData();
    formData.append('anchorId', anchorId);
    formData.append('programId', programId);
    if (subProgramId) {
      formData.append('subProgramId', subProgramId);
    }
    formData.append('file', file);
    return apiClient.post('/api/v1/portal/anchor/invoices/upload', formData);
  },
  anchorVerifyInvoice: (invoiceId: string) =>
    apiClient.post(`/api/v1/portal/anchor/invoices/${invoiceId}/verify`),
  anchorConfirmInvoice: (invoiceId: string) =>
    apiClient.post(`/api/v1/portal/anchor/invoices/${invoiceId}/confirm`),
  anchorApproveSellerInvoice: (invoiceId: string) =>
    apiClient.post(`/api/v1/portal/anchor/invoices/${invoiceId}/approve`),
  anchorRejectSellerInvoice: (invoiceId: string, reason?: string) =>
    apiClient.post(`/api/v1/portal/anchor/invoices/${invoiceId}/reject`, reason ? { reason } : {}),
  earlyPayEnabled: () => apiClient.get('/api/v1/portal/anchor/early-pay/enabled'),
  earlyPaySubPrograms: () => apiClient.get('/api/v1/portal/anchor/early-pay/sub-programs'),
  earlyPayParameters: (subProgramId?: string) =>
    apiClient.get('/api/v1/portal/anchor/early-pay/parameters', {
      params: subProgramId ? { subProgramId } : {},
    }),
  earlyPayCreateParameter: (data: Record<string, unknown>) =>
    apiClient.post('/api/v1/portal/anchor/early-pay/parameters', data),
  earlyPayBorrowers: (subProgramId: string) =>
    apiClient.get('/api/v1/portal/anchor/early-pay/borrowers', { params: { subProgramId } }),
  earlyPayUpdateBorrower: (membershipId: string, enableEarlyPay: string) =>
    apiClient.put(`/api/v1/portal/anchor/early-pay/borrowers/${membershipId}`, { enableEarlyPay }),
  earlyPayRequests: (status?: string, subProgramId?: string) =>
    apiClient.get('/api/v1/portal/anchor/early-pay/requests', {
      params: {
        ...(status ? { status } : {}),
        ...(subProgramId ? { subProgramId } : {}),
      },
    }),
  earlyPayApproveRequests: (requestIds: string[]) =>
    apiClient.post('/api/v1/portal/anchor/early-pay/requests/approve', { requestIds }),
  earlyPayRejectRequests: (requestIds: string[]) =>
    apiClient.post('/api/v1/portal/anchor/early-pay/requests/reject', { requestIds }),
  earlyPayPayments: () => apiClient.get('/api/v1/portal/anchor/early-pay/payments'),
  earlyPayClosed: () => apiClient.get('/api/v1/portal/anchor/early-pay/closed'),
  earlyPayRepayments: () => apiClient.get('/api/v1/portal/anchor/early-pay/repayments'),
  earlyPayUploadRepayments: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient.post('/api/v1/portal/anchor/early-pay/repayments/upload', formData);
  },
};

export const earlyPayApi = {
  todayParameter: (subProgramId: string) =>
    apiClient.get('/api/v1/invoices/early-pay/parameters/today', { params: { subProgramId } }),
  createRequest: (data: Record<string, unknown>) =>
    apiClient.post('/api/v1/invoices/early-pay/requests', data),
  listRepayments: () => apiClient.get('/api/v1/invoices/early-pay/repayments'),
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
  eventSettings: () => apiClient.get('/api/v1/notifications/event-settings'),
  updateEventSetting: (eventCode: string, enabled: boolean) =>
    apiClient.put(`/api/v1/notifications/event-settings/${eventCode}`, { enabled }),
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

export interface PaymentCheckoutLine {
  id: string;
  borrowerId: string;
  invoiceId: string;
  loanId?: string;
  subProgramId?: string;
  programId: string;
  invoiceNumber?: string;
  amountToPay: number;
  discountAmount: number;
  status: string;
}

export interface PayuInitiatePayload {
  baseUrl: string;
  key: string;
  txnid: string;
  amount: string;
  productinfo: string;
  firstname: string;
  email: string;
  phone?: string;
  udf1?: string;
  hash: string;
  surl: string;
  furl: string;
  transactionId?: string;
}

export interface PaymentInProgressRow {
  id: string;
  pgTransactionId: string;
  invoiceId: string;
  loanId: string;
  borrowerId: string;
  principalAmount: number;
  discountAmount: number;
  pipStatus: string;
  createdAt?: string;
}

export const paymentCartApi = {
  listLines: (borrowerId?: string) =>
    apiClient.get<{ status?: string; data?: PaymentCheckoutLine[] }>(
      '/api/v1/portal/borrower/payments/checkout/lines',
      { params: borrowerId ? { borrowerId } : undefined },
    ),
  count: (borrowerId?: string) =>
    apiClient.get<{ status?: string; data?: number }>(
      '/api/v1/portal/borrower/payments/checkout/count',
      { params: borrowerId ? { borrowerId } : undefined },
    ),
  paymentMethod: (borrowerId?: string) =>
    apiClient.get<{ status?: string; data?: { paymentMethod: string } }>(
      '/api/v1/portal/borrower/payments/checkout/payment-method',
      { params: borrowerId ? { borrowerId } : undefined },
    ),
  addLine: (invoiceId: string, borrowerId?: string, amount?: number) =>
    apiClient.post(
      '/api/v1/portal/borrower/payments/checkout/lines',
      { invoiceId, ...(amount != null ? { amount } : {}) },
      { params: borrowerId ? { borrowerId } : undefined },
    ),
  addBulk: (invoiceIds: string[], borrowerId?: string) =>
    apiClient.post(
      '/api/v1/portal/borrower/payments/checkout/lines/bulk',
      { invoiceIds },
      { params: borrowerId ? { borrowerId } : undefined },
    ),
  removeLine: (lineId: string, borrowerId?: string) =>
    apiClient.delete(`/api/v1/portal/borrower/payments/checkout/lines/${lineId}`, {
      params: borrowerId ? { borrowerId } : undefined,
    }),
  clearCart: (borrowerId?: string) =>
    apiClient.delete('/api/v1/portal/borrower/payments/checkout/lines', {
      params: borrowerId ? { borrowerId } : undefined,
    }),
};

export const payuApi = {
  initiate: (portalSource: 'PLP' | 'LOS' = 'PLP', borrowerId?: string) =>
    apiClient.post<{ status?: string; data?: PayuInitiatePayload }>(
      '/api/v1/portal/borrower/payments/payu/initiate',
      { portalSource },
      { params: borrowerId ? { borrowerId } : undefined },
    ),
};

export const pgSettlementApi = {
  listOpenPip: () =>
    apiClient.get<{ status?: string; data?: PaymentInProgressRow[] }>(
      '/api/v1/payments/settlements/pip',
      { headers: lenderLoanActionHeaders() },
    ),
  listTransactions: () =>
    apiClient.get('/api/v1/payments/settlements/transactions', {
      headers: lenderLoanActionHeaders(),
    }),
  createBatch: (payload: {
    settlementDate: string;
    settlementUtr: string;
    pipIds: string[];
    remarks?: string;
  }) =>
    apiClient.post('/api/v1/payments/settlements/batches', payload, {
      headers: lenderLoanActionHeaders(),
    }),
};
