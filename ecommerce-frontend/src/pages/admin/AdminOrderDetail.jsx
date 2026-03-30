import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import api from '../../api/axios';
import formatCurrency from '../../utils/formatCurrency';
import StatusBadge from '../../components/StatusBadge';

const STATUS_TRANSITIONS = {
  CONFIRMED: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['DELIVERED'],
};

export default function AdminOrderDetail() {
  const { orderId } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [newStatus, setNewStatus] = useState('');
  const [updating, setUpdating] = useState(false);
  const [updateMsg, setUpdateMsg] = useState('');

  useEffect(() => {
    setLoading(true);
    api
      .get(`/admin/orders/${orderId}`)
      .then((res) => setOrder(res.data))
      .catch(() => setError('Failed to load order.'))
      .finally(() => setLoading(false));
  }, [orderId]);

  function handleStatusUpdate() {
    if (!newStatus) return;
    setUpdating(true);
    setUpdateMsg('');
    api
      .patch(`/admin/orders/${orderId}/status`, { status: newStatus })
      .then((res) => {
        setOrder(res.data);
        setNewStatus('');
        setUpdateMsg('Status updated successfully.');
      })
      .catch((err) => setUpdateMsg(err.response?.data?.message || 'Update failed.'))
      .finally(() => setUpdating(false));
  }

  function formatDate(dateStr) {
    return new Date(dateStr).toLocaleString('en-IN', {
      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  }

  if (loading) {
    return <div className="animate-pulse space-y-4">{Array.from({ length: 4 }).map((_, i) => <div key={i} className="h-16 bg-gray-200 rounded" />)}</div>;
  }

  if (error) {
    return (
      <div>
        <Link to="/admin/orders" className="text-indigo-600 hover:underline text-sm">&larr; Back to Orders</Link>
        <div className="mt-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-md">{error}</div>
      </div>
    );
  }

  if (!order) return null;

  const allowedStatuses = STATUS_TRANSITIONS[order.orderStatus] || [];

  return (
    <div>
      <Link to="/admin/orders" className="text-indigo-600 hover:underline text-sm">&larr; Back to Orders</Link>

      <h1 className="text-2xl font-bold text-gray-900 mt-4 mb-6">Order #{order.orderId}</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
        <div className="bg-white border border-gray-200 rounded-lg p-5 space-y-3">
          <h2 className="text-sm font-semibold text-gray-500 uppercase">Order Info</h2>
          <div className="flex justify-between"><span className="text-gray-600">Status</span><StatusBadge status={order.orderStatus} /></div>
          <div className="flex justify-between"><span className="text-gray-600">Payment</span><StatusBadge status={order.paymentStatus} /></div>
          <div className="flex justify-between"><span className="text-gray-600">Method</span><span className="font-medium">{order.paymentMethod}</span></div>
          <div className="flex justify-between"><span className="text-gray-600">Total</span><span className="font-medium">{formatCurrency(order.totalAmount)}</span></div>
          <div className="flex justify-between"><span className="text-gray-600">Date</span><span>{formatDate(order.createdAt)}</span></div>
          {order.gatewayOrderId && (
            <div className="flex justify-between"><span className="text-gray-600">Gateway ID</span><span className="text-xs font-mono">{order.gatewayOrderId}</span></div>
          )}
        </div>

        <div className="bg-white border border-gray-200 rounded-lg p-5 space-y-3">
          <h2 className="text-sm font-semibold text-gray-500 uppercase">Customer</h2>
          <div className="flex justify-between"><span className="text-gray-600">User ID</span><span className="font-medium">{order.userId}</span></div>
          <div className="flex justify-between"><span className="text-gray-600">Email</span><span className="font-medium">{order.userEmail}</span></div>
        </div>
      </div>

      {allowedStatuses.length > 0 && (
        <div className="bg-white border border-gray-200 rounded-lg p-5 mb-8">
          <h2 className="text-sm font-semibold text-gray-500 uppercase mb-3">Update Status</h2>
          <div className="flex flex-wrap items-center gap-3">
            <select
              value={newStatus}
              onChange={(e) => setNewStatus(e.target.value)}
              className="px-3 py-2 text-sm border border-gray-300 rounded-md bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">Select new status</option>
              {allowedStatuses.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
            <button
              onClick={handleStatusUpdate}
              disabled={!newStatus || updating}
              className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {updating ? 'Updating…' : 'Update'}
            </button>
          </div>
          {updateMsg && (
            <p className={`mt-2 text-sm ${updateMsg.includes('success') ? 'text-green-600' : 'text-red-600'}`}>{updateMsg}</p>
          )}
        </div>
      )}

      <div className="bg-white border border-gray-200 rounded-lg overflow-x-auto">
        <table className="w-full text-left min-w-[500px]">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Product</th>
              <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Qty</th>
              <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Price</th>
              <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Subtotal</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {order.items?.map((item, i) => (
              <tr key={i}>
                <td className="px-4 py-3 text-sm text-gray-900">{item.productName}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{item.quantity}</td>
                <td className="px-4 py-3 text-sm text-gray-600">{formatCurrency(item.priceAtPurchase)}</td>
                <td className="px-4 py-3 text-sm font-medium text-gray-900">{formatCurrency(item.priceAtPurchase * item.quantity)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot className="border-t border-gray-200 bg-gray-50">
            <tr>
              <td colSpan="3" className="px-4 py-3 text-sm font-semibold text-gray-700 text-right">Total</td>
              <td className="px-4 py-3 text-sm font-bold text-gray-900">{formatCurrency(order.totalAmount)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  );
}
