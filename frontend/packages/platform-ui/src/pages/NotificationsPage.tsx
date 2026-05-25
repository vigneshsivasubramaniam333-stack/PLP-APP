import { useState, useEffect } from 'react';
import { notificationApi } from '@plp/shared';

interface Template {
  id: string;
  templateCode: string;
  channel: string;
  subject: string;
  bodyTemplate: string;
  isActive: boolean;
}

const channelStyles: Record<string, string> = {
  EMAIL: 'bg-blue-50 text-blue-700 ring-1 ring-blue-600/20',
  SMS: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20',
  WHATSAPP: 'bg-green-50 text-green-700 ring-1 ring-green-600/20',
  PUSH: 'bg-violet-50 text-violet-700 ring-1 ring-violet-600/20',
};

export default function NotificationsPage() {
  const [templates, setTemplates] = useState<Template[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editData, setEditData] = useState({ subject: '', bodyTemplate: '' });

  useEffect(() => {
    notificationApi.templates()
      .then((res) => setTemplates(res.data.data || res.data || []))
      .catch(() => setTemplates([]))
      .finally(() => setLoading(false));
  }, []);

  const startEdit = (t: Template) => {
    setEditingId(t.id);
    setEditData({ subject: t.subject, bodyTemplate: t.bodyTemplate });
  };

  const saveEdit = async (id: string) => {
    try {
      await notificationApi.updateTemplate(id, editData);
      setTemplates(templates.map((t) => t.id === id ? { ...t, ...editData } : t));
      setEditingId(null);
    } catch (err) {
      console.error('Failed to update template:', err);
    }
  };

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Notifications</h1>
        <p className="text-sm text-slate-500 mt-1">Manage notification templates and delivery settings</p>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center h-48">
            <div className="animate-pulse text-slate-400 text-sm">Loading templates...</div>
          </div>
        ) : templates.length === 0 ? (
          <div className="flex items-center justify-center h-48">
            <div className="text-center">
              <div className="text-slate-400 text-sm">No templates found</div>
              <p className="text-xs text-slate-400 mt-1">Notification templates are seeded during database migration</p>
            </div>
          </div>
        ) : (
          <div className="divide-y divide-slate-100">
            {templates.map((t) => (
              <div key={t.id} className="px-5 py-4 hover:bg-slate-50/50">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                        channelStyles[t.channel] || 'bg-slate-50 text-slate-600'
                      }`}>
                        {t.channel}
                      </span>
                      <span className="text-sm font-semibold text-slate-700">{t.templateCode}</span>
                    </div>

                    {editingId === t.id ? (
                      <div className="mt-3 space-y-3">
                        <div>
                          <label className="block text-xs font-medium text-slate-500 mb-1">Subject</label>
                          <input value={editData.subject} onChange={(e) => setEditData({ ...editData, subject: e.target.value })}
                            className="w-full px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none" />
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-slate-500 mb-1">Body Template</label>
                          <textarea value={editData.bodyTemplate} onChange={(e) => setEditData({ ...editData, bodyTemplate: e.target.value })}
                            rows={3}
                            className="w-full px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none resize-none" />
                        </div>
                        <div className="flex gap-2">
                          <button onClick={() => saveEdit(t.id)}
                            className="px-3 py-1.5 text-xs font-semibold bg-blue-600 text-white rounded-md hover:bg-blue-700">
                            Save Changes
                          </button>
                          <button onClick={() => setEditingId(null)}
                            className="px-3 py-1.5 text-xs font-medium text-slate-600 bg-white border border-slate-200 rounded-md hover:bg-slate-50">
                            Cancel
                          </button>
                        </div>
                      </div>
                    ) : (
                      <>
                        <div className="text-sm text-slate-700 mt-1">{t.subject}</div>
                        <div className="text-xs text-slate-400 mt-1 line-clamp-2">{t.bodyTemplate}</div>
                      </>
                    )}
                  </div>
                  {editingId !== t.id && (
                    <button onClick={() => startEdit(t)}
                      className="shrink-0 px-3 py-1.5 text-xs font-medium text-slate-600 bg-white border border-slate-200 rounded-md hover:bg-slate-50">
                      Edit
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
