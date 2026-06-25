import axios, { type AxiosResponse } from 'axios';
import { apiClient } from './client';
import { invoiceAccessHeaders } from '../auth/lenderLoanHeaders';

export type DigitalInvoiceFile = {
  blob: Blob;
  contentType: string;
  filename: string;
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

export function triggerDigitalInvoiceBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.rel = 'noopener noreferrer';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

export async function fetchDigitalInvoiceFile(invoiceId: string): Promise<DigitalInvoiceFile> {
  try {
    const res = await apiClient.get<Blob>(`/api/v1/invoices/${invoiceId}/digital-invoice/download`, {
      responseType: 'blob',
      headers: invoiceAccessHeaders(),
    });
    return blobResponseToDigitalInvoiceFile(res);
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

export function blobResponseToDigitalInvoiceFile(res: AxiosResponse<Blob>): DigitalInvoiceFile {
  const blob = res.data;
  const cd = res.headers['content-disposition'];
  const parsedName = parseFilenameFromContentDisposition(
    typeof cd === 'string' ? cd : Array.isArray(cd) ? cd[0] : undefined,
  );
  const ctHeader = res.headers['content-type'];
  const contentType =
    (typeof ctHeader === 'string' ? ctHeader : Array.isArray(ctHeader) ? ctHeader[0] : '') ||
    blob.type ||
    'application/octet-stream';
  return {
    blob,
    contentType,
    filename: parsedName || 'digital-invoice',
  };
}

export function isPdfContentType(ct: string): boolean {
  return ct.includes('application/pdf');
}

export function isImageContentType(ct: string): boolean {
  return ct.startsWith('image/');
}

/** Downloads the digital invoice file (no in-portal preview). */
export async function openDigitalInvoiceDownload(invoiceId: string): Promise<void> {
  const file = await fetchDigitalInvoiceFile(invoiceId);
  triggerDigitalInvoiceBlobDownload(file.blob, file.filename);
}
