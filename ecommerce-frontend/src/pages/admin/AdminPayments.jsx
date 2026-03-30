import { useEffect, useState, useCallback } from 'react';
import api from '../../api/axios';
import formatCurrency from '../../utils/formatCurrency';
import StatusBadge from '../../components/StatusBadge';
import Pagination from '../../components/Pagination';

const PAYMENT_STATUSES = ['SUCCESS', 'FAILED', 'PENDING', 'AWAITING_PAYMENT', 'REFUNDED'];

export default function AdminPayments() {
  const [payments, setPayments] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showFailures, setShowFailures] = useState(false);

  // inline edit state
  const [editId, setEditId] = useState(null);
  const [editStatus, setEditStatus] = useState('');
  const [editGatewayId, setEditGatewayId] = useState('');
  const [editReason, setEditReason] = useState('');
  const [updating, setUpdating] = useState(false);
  const [updateMsg, setUpdateMsg] = useState('');

  const fetchPayments = useCallback(() => {
    setLoading(true);
    setError('');
    const url = showFailures ? '/admin/payments/failures' : '/admin/payments';
    api
      .get(url, { params: { page, size: 15, sort: 'createdAt,desc' } })
      .then((res) => {
        setPayments(res.data.content);
        setTotalPages(res.data.totalPages);
      })
      .catch(() => setError('Failed to load payments.'))
      .finally(() => setLoading(false));
  }, [page, showFailures]);

  useEffect(() => { fetchPayments(); }, [fetchPayments]);

  function startEdit(p) {
    setEditId(p.paymentId);
    setEditStatus(p.paymentStatus);
    setEditGatewayId(p.gatewayPaymentId || '');
    setEditReason('');
    setUpdateMsg('');
  }

  function cancelEdit() {
    setEditId(null);
    setUpdateMsg('');
  }

  function saveEdit(paymentId) {
    setUpdating(true);
    setUpdateMsg('');
    const body = { status: editStatus };
    if (editGatewayId) body.gatewayPaymentId = editGatewayId;
    if (editReason) body.reason = editReason;

    api
      .patch(`/admin/payments/${paymentId}/status`, body)
      .then((res) => {
        setPayments((prev) => prev.map((p) => (p.paymentId === paymentId ? res.data : p)));
        setEditId(null);
        setUpdateMsg('Updated successfully.');
      })
      .catch((err) => setUpdateMsg(err.response?.data?.message || 'Update failed.'))
      .finally(() => setUpdating(false));
  }

  function formatDate(dateStr) {
    return new Date(dateStr).toLocaleDateString('en-IN', {
      day: 'numeric', month: 'short', year: 'numeric',
    });
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Payments</h1>
        <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
          <input
            type="checkbox"
            checked={showFailures}
            onChange={(e) => { setShowFailures(e.target.checked); setPage(0); }}
            className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
          />
          Show failures only
        </label>
      </div>

      {updateMsg && !editId && (
        <div className={`p-3 mb-4 rounded-md text-sm ${updateMsg.includes('success') ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'}`}>{updateMsg}</div>
      )}

      {error && <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-md mb-4">{error}</div>}

      {loading && (
        <div className="animate-pulse space-y-3">
          {Array.from({ length: 6 }).map((_, i) => <div key={i} className="h-12 bg-gray-200 rounded" />)}
        </div>
      )}

      {!loading && payments.length === 0 && !error && (
        <p className="text-gray-500 py-8 text-center">No payments found.</p>
      )}

      {!loading && payments.length > 0 && (
        <>
          {/* Desktop table */}
          <div className="hidden md:block bg-white rounded-lg shadow-sm border border-gray-200 overflow-x-auto">
            <table className="w-full text-left min-w-[900px]">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">ID</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Order</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">User</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Method</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Amount</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Date</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {payments.map((p) => (
                  <tr key={p.paymentId} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-sm font-medium text-gray-900">#{p.paymentId}</td>
                    <td className="px-4 py-3 text-sm text-indigo-600">#{p.orderId}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{p.userEmail}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{p.paymentMethod}</td>
                    <td className="px-4 py-3 text-sm font-medium text-gray-900">{formatCurrency(p.amount)}</td>
                    <td className="px-4 py-3"><StatusBadge status={p.paymentStatus} /></td>
                    <td className="px-4 py-3 text-sm text-gray-600">{formatDate(p.createdAt)}</td>
                    <td className="px-4 py-3">
                      {editId === p.paymentId ? (
                        <div className="space-y-2 min-w-[200px]">
                          <select
                            value={editStatus}
                            onChange={(e) => setEditStatus(e.target.value)}
                            className="w-full px-2 py-1 text-sm border border-gray-300 rounded-md"
                          >
                            {PAYMENT_STATUSES.map((s) => (
                              <option key={s} value={s}>{s}</option>
                            ))}
                          </select>
                          <input
                            type="text"
                            placeholder="Gateway Payment ID"
                            value={editGatewayId}
                            onChange={(e) => setEditGatewayId(e.target.value)}
                            className="w-full px-2 py-1 text-sm border border-gray-300 rounded-md"
                          />
                          <input
                            type="text"
                            placeholder="Reason (optional)"
                            value={editReason}
                            onChange={(e) => setEditReason(e.target.value)}
                            className="w-full px-2 py-1 text-sm border border-gray-300 rounded-md"
                          />
                          <div className="flex gap-2">
                            <button
                              onClick={() => saveEdit(p.paymentId)}
                              disabled={updating}
                              className="px-3 py-1 text-xs bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:opacity-50"
                            >
                              {updating ? 'Saving…' : 'Save'}
                            </button>
                            <button onClick={cancelEdit} className="px-3 py-1 text-xs bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300">
                              Cancel
                            </button>
                          </div>
                          {updateMsg && editId === p.paymentId && (
                            <p className={`text-xs ${updateMsg.includes('success') ? 'text-green-600' : 'text-red-600'}`}>{updateMsg}</p>
                          )}
                        </div>
                      ) : (
                        <button
                          onClick={() => startEdit(p)}
                          className="px-3 py-1 text-xs border border-gray-300 rounded-md hover:bg-gray-100"
                        >
                          Edit
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="md:hidden space-y-3">
            {payments.map((p) => (
              <div key={p.paymentId} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
                <div className="flex justify-between items-start mb-2">
                  <div>
                    <span className="text-sm font-medium text-gray-900">Payment #{p.paymentId}</span>
                    <span className="text-sm text-indigo-600 ml-2">Order #{p.orderId}</span>
                  </div>
                  <span className="text-xs text-gray-500">{formatDate(p.createdAt)}</span>
                </div>
                <p className="text-sm text-gray-600 mb-1 truncate">{p.userEmail}</p>
                <div className="flex justify-between items-center mb-2">
                  <span className="font-semibold text-gray-900">{formatCurrency(p.amount)}</span>
                  <span className="text-xs text-gray-500">{p.paymentMethod}</span>
                </div>
                <div className="flex items-center justify-between">
                  <StatusBadge status={p.paymentStatus} />
                  {editId === p.paymentId ? (
                    <div className="space-y-2 mt-2 w-full">
                      <select value={editStatus} onChange={(e) => setEditStatus(e.target.value)} className="w-full px-2 py-1 text-sm border border-gray-300 rounded-md">
                        {PAYMENT_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
                      </select>
                      <input type="text" placeholder="Gateway Payment ID" value={editGatewayId} onChange={(e) => setEditGatewayId(e.target.value)} className="w-full px-2 py-1 text-sm border border-gray-300 rounded-md" />
                      <input type="text" placeholder="Reason" value={editReason} onChange={(e) => setEditReason(e.target.value)} className="w-full px-2 py-1 text-sm border border-gray-300 rounded-md" />
                      <div className="flex gap-2">
                        <button onClick={() => saveEdit(p.paymentId)} disabled={updating} className="px-3 py-1 text-xs bg-indigo-600 text-white rounded-md disabled:opacity-50">{updating ? 'Saving…' : 'Save'}</button>
                        <button onClick={cancelEdit} className="px-3 py-1 text-xs bg-gray-200 text-gray-700 rounded-md">Cancel</button>
                      </div>
                      {updateMsg && editId === p.paymentId && <p className={`text-xs ${updateMsg.includes('success') ? 'text-green-600' : 'text-red-600'}`}>{updateMsg}</p>}
                    </div>
                  ) : (
                    <button onClick={() => startEdit(p)} className="px-3 py-1 text-xs border border-gray-300 rounded-md hover:bg-gray-100">Edit</button>
                  )}
                </div>
              </div>
            ))}
          </div>

          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
