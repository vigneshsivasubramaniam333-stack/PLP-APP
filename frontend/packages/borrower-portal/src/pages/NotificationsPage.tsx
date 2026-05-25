import { useState, useEffect } from 'react';
import { notificationApi, useAuth } from '@plp/shared';

interface NotificationEntry {
  id: string;
  channel: string;
  subject: string;
  body: string;
  referenceType: string;
  status: string;
  createdAt: string;
}

const channelIcon: Record<string, string> = {
  EMAIL: 'bg-blue-50 text-blue-600',
  SMS: 'bg-emerald-50 text-emerald-600',
  WHATSAPP: 'bg-green-50 text-green-600',
  PUSH: 'bg-violet-50 text-violet-600',
};

export default function NotificationsPage() {
  const { user } = useAuth();
  const [notifications, setNotifications] = useState<NotificationEntry[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (user?.userId) loadNotifications();
  }, [user]);

  async function loadNotifications() {
    setLoading(true);
    try {
      const res = await notificationApi.list(user!.userId);
      setNotifications(res.data?.data || []);
    } catch { setNotifications([]); }
    setLoading(false);
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Notifications</h1>
        <p className="text-sm text-slate-500 mt-1">Loan updates, payment reminders, and alerts</p>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-48">
          <div className="animate-pulse text-slate-400 text-sm">Loading notifications...</div>
        </div>
      ) : notifications.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
          <div className="text-slate-400 text-sm">No notifications yet</div>
          <p className="text-xs text-slate-400 mt-1">You'll see loan updates, payment reminders, and alerts here</p>
        </div>
      ) : (
        <div className="space-y-3">
          {notifications.map(n => (
            <div key={n.id} className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
              <div className="flex items-start gap-3">
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${channelIcon[n.channel] || 'bg-slate-50 text-slate-500'}`}>
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                    {n.channel === 'EMAIL'
                      ? <path strokeLinecap="round" strokeLinejoin="round" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                      : <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                    }
                  </svg>
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <h4 className="text-sm font-semibold text-slate-800 truncate">{n.subject}</h4>
                    <span className={`shrink-0 inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold ${
                      n.status === 'SENT' ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20' :
                      n.status === 'FAILED' ? 'bg-red-50 text-red-600 ring-1 ring-red-500/20' :
                      'bg-slate-50 text-slate-500 ring-1 ring-slate-400/20'
                    }`}>{n.status}</span>
                  </div>
                  <p className="text-xs text-slate-500 mt-1 line-clamp-2">{n.body}</p>
                  <div className="flex items-center gap-3 mt-2">
                    <span className="text-[11px] text-slate-400">
                      {new Date(n.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })}
                    </span>
                    <span className={`text-[10px] font-bold uppercase ${channelIcon[n.channel]?.split(' ')[1] || 'text-slate-500'}`}>
                      {n.channel}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
