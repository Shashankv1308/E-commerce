import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axios';
import formatCurrency from '../../utils/formatCurrency';
import StatusBadge from '../../components/StatusBadge';
import Pagination from '../../components/Pagination';

const ORDER_STATUSES = ['', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
const PAYMENT_STATUSES = ['', 'AWAITING_PAYMENT', 'PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'];

export default function AdminOrders() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [orderStatus, setOrderStatus] = useState('');
  const [paymentStatus, setPaymentStatus] = useState('');

  const fetchOrders = useCallback(() => {
    setLoading(true);
    setError('');
    const params = { page, size: 15, sort: 'createdAt,desc' };
    if (orderStatus) params.orderStatus = orderStatus;
    if (paymentStatus) params.paymentStatus = paymentStatus;

    api
      .get('/admin/orders', { params })
      .then((res) => {
        setOrders(res.data.content);
        setTotalPages(res.data.totalPages);
      })
      .catch(() => setError('Failed to load orders.'))
      .finally(() => setLoading(false));
  }, [page, orderStatus, paymentStatus]);

  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  function handleFilterChange(setter) {
    return (e) => { setter(e.target.value); setPage(0); };
  }

  function formatDate(dateStr) {
    return new Date(dateStr).toLocaleDateString('en-IN', {
      day: 'numeric', month: 'short', year: 'numeric',
    });
  }

  const selectClass = 'px-3 py-2 text-sm border border-gray-300 rounded-md bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500';

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">All Orders</h1>

      <div className="flex flex-wrap gap-3 mb-5">
        <select value={orderStatus} onChange={handleFilterChange(setOrderStatus)} className={selectClass}>
          <option value="">All Order Status</option>
          {ORDER_STATUSES.filter(Boolean).map((s) => (
            <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
          ))}
        </select>
        <select value={paymentStatus} onChange={handleFilterChange(setPaymentStatus)} className={selectClass}>
          <option value="">All Payment Status</option>
          {PAYMENT_STATUSES.filter(Boolean).map((s) => (
            <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
          ))}
        </select>
      </div>

      {error && <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-md mb-4">{error}</div>}

      {loading && (
        <div className="animate-pulse space-y-3">
          {Array.from({ length: 6 }).map((_, i) => <div key={i} className="h-12 bg-gray-200 rounded" />)}
        </div>
      )}

      {!loading && orders.length === 0 && !error && (
        <p className="text-gray-500 py-8 text-center">No orders found.</p>
      )}

      {!loading && orders.length > 0 && (
        <>
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-x-auto">
            <table className="w-full text-left min-w-[700px]">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Order #</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">User</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Date</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Total</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Payment</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Method</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {orders.map((o) => (
                  <tr
                    key={o.orderId}
                    onClick={() => navigate(`/admin/orders/${o.orderId}`)}
                    className="hover:bg-gray-50 cursor-pointer"
                  >
                    <td className="px-4 py-3 text-sm font-medium text-indigo-600">#{o.orderId}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{o.userEmail}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{formatDate(o.createdAt)}</td>
                    <td className="px-4 py-3 text-sm font-medium text-gray-900">{formatCurrency(o.totalAmount)}</td>
                    <td className="px-4 py-3"><StatusBadge status={o.orderStatus} /></td>
                    <td className="px-4 py-3"><StatusBadge status={o.paymentStatus} /></td>
                    <td className="px-4 py-3 text-sm text-gray-600">{o.paymentMethod}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
