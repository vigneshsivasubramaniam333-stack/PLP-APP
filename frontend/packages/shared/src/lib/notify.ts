import toast from 'react-hot-toast';
import { extractApiErrorMessage } from '../api/extractApiErrorMessage';

export function notifySuccess(message: string) {
  toast.success(message);
}

export function notifyError(err: unknown, fallback: string) {
  toast.error(extractApiErrorMessage(err, fallback));
}

export function notifyErrorMessage(message: string) {
  toast.error(message);
}

export function notifyInfo(message: string) {
  toast(message, { className: 'bt-toast bt-toast-info' });
}
