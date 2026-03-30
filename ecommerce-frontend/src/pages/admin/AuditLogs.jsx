import { useEffect, useState, useCallback } from 'react';
import api from '../../api/axios';
import Pagination from '../../components/Pagination';

const ACTION_TYPES = ['', 'ORDER_STATUS_UPDATE', 'PAYMENT_STATUS_UPDATE', 'INVENTORY_ADJUSTMENT', 'ADMIN_CREATION', 'PRODUCT_CREATION'];

export default function AuditLogs() {
  const [logs, setLogs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionType, setActionType] = useState('');
  const [adminEmail, setAdminEmail] = useState('');

  const fetchLogs = useCallback(() => {
    setLoading(true);
    setError('');
    const params = { page, size: 20, sort: 'createdAt,desc' };
    if (actionType) params.actionType = actionType;
    if (adminEmail.trim()) params.adminEmail = adminEmail.trim();

    api
      .get('/admin/audit-logs', { params })
      .then((res) => {
        setLogs(res.data.content);
        setTotalPages(res.data.totalPages);
      })
      .catch(() => setError('Failed to load audit logs.'))
      .finally(() => setLoading(false));
  }, [page, actionType, adminEmail]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  function formatDate(dateStr) {
    return new Date(dateStr).toLocaleString('en-IN', {
      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  }

  const selectClass = 'px-3 py-2 text-sm border border-gray-300 rounded-md bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500';

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Audit Logs</h1>

      <div className="flex flex-wrap gap-3 mb-5">
        <select
          value={actionType}
          onChange={(e) => { setActionType(e.target.value); setPage(0); }}
          className={selectClass}
        >
          <option value="">All Actions</option>
          {ACTION_TYPES.filter(Boolean).map((a) => (
            <option key={a} value={a}>{a.replace(/_/g, ' ')}</option>
          ))}
        </select>
        <input
          type="text"
          placeholder="Filter by admin email"
          value={adminEmail}
          onChange={(e) => setAdminEmail(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { setPage(0); fetchLogs(); } }}
          className={selectClass + ' min-w-[200px]'}
        />
        <button
          onClick={() => { setPage(0); fetchLogs(); }}
          className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700"
        >
          Filter
        </button>
      </div>

      {error && <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-md mb-4">{error}</div>}

      {loading && (
        <div className="animate-pulse space-y-3">
          {Array.from({ length: 6 }).map((_, i) => <div key={i} className="h-12 bg-gray-200 rounded" />)}
        </div>
      )}

      {!loading && logs.length === 0 && !error && (
        <p className="text-gray-500 py-8 text-center">No audit logs found.</p>
      )}

      {!loading && logs.length > 0 && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block bg-white rounded-lg shadow-sm border border-gray-200 overflow-x-auto">
            <table className="w-full text-left min-w-[800px]">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">ID</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Admin</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Action</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Target</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Details</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {logs.map((log) => (
                  <tr key={log.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-sm text-gray-900">{log.id}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{log.adminEmail}</td>
                    <td className="px-4 py-3">
                      <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full bg-gray-100 text-gray-700">
                        {log.actionType?.replace(/_/g, ' ')}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {log.targetEntity} #{log.targetId}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500 max-w-[250px] truncate" title={log.details}>
                      {log.details}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">{formatDate(log.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {logs.map((log) => (
              <div key={log.id} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
                <div className="flex justify-between items-start mb-2">
                  <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full bg-gray-100 text-gray-700">
                    {log.actionType?.replace(/_/g, ' ')}
                  </span>
                  <span className="text-xs text-gray-500">{formatDate(log.createdAt)}</span>
                </div>
                <p className="text-sm text-gray-600 mb-1">{log.adminEmail}</p>
                <p className="text-sm text-gray-700 mb-1">
                  <span className="text-gray-500">Target:</span> {log.targetEntity} #{log.targetId}
                </p>
                {log.details && (
                  <p className="text-xs text-gray-500 break-words">{log.details}</p>
                )}
              </div>
            ))}
          </div>

          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
