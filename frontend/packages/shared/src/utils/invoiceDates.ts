/** Returns an error message when due date is before invoice date; otherwise null. */
export function invoiceDueDateError(invoiceDate: string, dueDate: string): string | null {
  if (!invoiceDate || !dueDate) return null;
  if (dueDate < invoiceDate) return 'Due date cannot be before invoice date';
  return null;
}
