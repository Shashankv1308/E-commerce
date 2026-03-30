import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import api from '../api/axios';
import formatCurrency from '../utils/formatCurrency';
import StatusBadge from '../components/StatusBadge';

export default function OrderDetail() {
  const { orderId } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  useEffect(() => {
    api
      .get(`/orders/${orderId}`)
      .then((res) => setOrder(res.data))
      .catch(() => setError('Failed to load order.'))
      .finally(() => setLoading(false));
  }, [orderId]);

  async function handleCancel() {
    setCancelling(true);
    setShowConfirm(false);
    try {
      const res = await api.post(`/orders/${orderId}/cancel`);
      setOrder(res.data);
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data || 'Failed to cancel order.');
    } finally {
      setCancelling(false);
    }
  }

  function formatDate(dateStr) {
    return new Date(dateStr).toLocaleString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  if (loading) {
    return (
      <div className="animate-pulse space-y-4 max-w-3xl">
        <div className="h-7 bg-gray-200 rounded w-40" />
        <div className="h-32 bg-gray-200 rounded" />
        <div className="h-48 bg-gray-200 rounded" />
      </div>
    );
  }

  if (error && !order) {
    return (
      <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-md">{error}</div>
    );
  }

  if (!order) return null;

  const canCancel = order.orderStatus === 'CONFIRMED';

  return (
    <div className="max-w-3xl">
      <Link to="/orders" className="text-sm text-indigo-600 hover:text-indigo-500 mb-4 inline-block">
        ← Back to Orders
      </Link>

      <h1 className="text-2xl font-bold text-gray-900 mb-6">Order #{order.orderId}</h1>

      {error && (
        <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-md text-sm">{error}</div>
      )}

      {/* Order header */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5 mb-5">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <div>
            <p className="text-xs text-gray-500 uppercase font-medium">Order Status</p>
            <div className="mt-1"><StatusBadge status={order.orderStatus} /></div>
          </div>
          <div>
            <p className="text-xs text-gray-500 uppercase font-medium">Payment Status</p>
            <div className="mt-1"><StatusBadge status={order.paymentStatus} /></div>
          </div>
          <div>
            <p className="text-xs text-gray-500 uppercase font-medium">Payment Method</p>
            <p className="mt-1 text-sm font-medium text-gray-900">{order.paymentMethod}</p>
          </div>
          <div>
            <p className="text-xs text-gray-500 uppercase font-medium">Date</p>
            <p className="mt-1 text-sm text-gray-700">{formatDate(order.createdAt)}</p>
          </div>
        </div>
      </div>

      {/* Items table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden mb-5">
        {/* Desktop */}
        <table className="hidden sm:table w-full text-left">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-5 py-3 text-xs font-medium text-gray-500 uppercase">Product</th>
              <th className="px-5 py-3 text-xs font-medium text-gray-500 uppercase">Price</th>
              <th className="px-5 py-3 text-xs font-medium text-gray-500 uppercase">Qty</th>
              <th className="px-5 py-3 text-xs font-medium text-gray-500 uppercase">Subtotal</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {order.items.map((item) => (
              <tr key={item.productId}>
                <td className="px-5 py-3 text-sm font-medium text-gray-900">{item.productName}</td>
                <td className="px-5 py-3 text-sm text-gray-600">{formatCurrency(item.priceAtPurchase)}</td>
                <td className="px-5 py-3 text-sm text-gray-600">{item.quantity}</td>
                <td className="px-5 py-3 text-sm font-medium text-gray-900">{formatCurrency(item.subtotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>

        {/* Mobile */}
        <div className="sm:hidden divide-y divide-gray-100">
          {order.items.map((item) => (
            <div key={item.productId} className="px-4 py-3">
              <div className="flex justify-between">
                <span className="text-sm font-medium text-gray-900">{item.productName}</span>
                <span className="text-sm font-medium text-gray-900">{formatCurrency(item.subtotal)}</span>
              </div>
              <p className="text-xs text-gray-500 mt-0.5">
                {formatCurrency(item.priceAtPurchase)} × {item.quantity}
              </p>
            </div>
          ))}
        </div>
      </div>

      {/* Total + Cancel */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-4 bg-white rounded-lg shadow-sm border border-gray-200 p-5">
        <p className="text-lg font-semibold text-gray-900">
          Total: <span className="text-green-700">{formatCurrency(order.totalAmount)}</span>
        </p>

        {canCancel && (
          <button
            onClick={() => setShowConfirm(true)}
            disabled={cancelling}
            className="px-5 py-2 text-sm font-medium rounded-md border border-red-300 text-red-600 hover:bg-red-50 disabled:opacity-50"
          >
            {cancelling ? 'Cancelling…' : 'Cancel Order'}
          </button>
        )}
      </div>

      {/* Confirmation dialog */}
      {showConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white rounded-lg shadow-xl p-6 mx-4 max-w-sm w-full">
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Cancel Order?</h3>
            <p className="text-sm text-gray-600 mb-5">
              This will cancel order #{order.orderId} and restore stock. This action cannot be undone.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setShowConfirm(false)}
                className="px-4 py-2 text-sm font-medium text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
              >
                Keep Order
              </button>
              <button
                onClick={handleCancel}
                className="px-4 py-2 text-sm font-medium text-white bg-red-600 rounded-md hover:bg-red-700"
              >
                Yes, Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
