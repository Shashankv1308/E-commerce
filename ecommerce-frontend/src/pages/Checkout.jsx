import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/axios';
import formatCurrency from '../utils/formatCurrency';

export default function Checkout() {
  const navigate = useNavigate();
  const [cart, setCart] = useState(null);
  const [loading, setLoading] = useState(true);
  const [paymentMethod, setPaymentMethod] = useState('COD');
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    api
      .get('/cart')
      .then((res) => setCart(res.data))
      .catch(() => setError('Failed to load cart.'))
      .finally(() => setLoading(false));
  }, []);

  async function handlePlaceOrder() {
    setError('');
    setPlacing(true);

    const idempotencyKey = crypto.randomUUID();

    try {
      const res = await api.post('/orders', { paymentMethod, idempotencyKey });
      navigate(`/orders/${res.data.orderId}`, { replace: true });
    } catch (err) {
      setError(
        err.response?.data?.message || err.response?.data || 'Failed to place order. Please try again.'
      );
    } finally {
      setPlacing(false);
    }
  }

  if (loading) {
    return (
      <div className="animate-pulse space-y-4 max-w-lg mx-auto">
        <div className="h-7 bg-gray-200 rounded w-32" />
        <div className="h-40 bg-gray-200 rounded" />
        <div className="h-10 bg-gray-200 rounded" />
      </div>
    );
  }

  const items = cart?.items || [];
  if (items.length === 0) {
    return (
      <div className="text-center py-16">
        <h2 className="text-lg font-semibold text-gray-900">Nothing to checkout</h2>
        <p className="mt-1 text-sm text-gray-500">Your cart is empty.</p>
        <Link
          to="/products"
          className="mt-4 inline-block px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-md hover:bg-indigo-700"
        >
          Browse Products
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Checkout</h1>

      {error && (
        <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-md text-sm">
          {error}
        </div>
      )}

      {/* Order summary */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5 mb-5">
        <h2 className="text-sm font-semibold text-gray-700 uppercase mb-3">Order Summary</h2>
        <div className="divide-y divide-gray-100">
          {items.map((item) => (
            <div key={item.productId} className="flex justify-between py-2 text-sm">
              <span className="text-gray-700">
                {item.productName} <span className="text-gray-400">× {item.quantity}</span>
              </span>
              <span className="font-medium text-gray-900">{formatCurrency(item.subtotal)}</span>
            </div>
          ))}
        </div>
        <div className="mt-3 pt-3 border-t border-gray-200 flex justify-between">
          <span className="font-semibold text-gray-900">Total</span>
          <span className="font-bold text-lg text-green-700">{formatCurrency(cart.totalAmount)}</span>
        </div>
      </div>

      {/* Payment method */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5 mb-5">
        <h2 className="text-sm font-semibold text-gray-700 uppercase mb-3">Payment Method</h2>
        <div className="space-y-3">
          <label className="flex items-center gap-3 cursor-pointer">
            <input
              type="radio"
              name="paymentMethod"
              value="COD"
              checked={paymentMethod === 'COD'}
              onChange={(e) => setPaymentMethod(e.target.value)}
              className="h-4 w-4 text-indigo-600 focus:ring-indigo-500"
            />
            <div>
              <span className="text-sm font-medium text-gray-900">Cash on Delivery</span>
              <p className="text-xs text-gray-500">Pay when your order arrives</p>
            </div>
          </label>
          <label className="flex items-center gap-3 cursor-pointer">
            <input
              type="radio"
              name="paymentMethod"
              value="ONLINE"
              checked={paymentMethod === 'ONLINE'}
              onChange={(e) => setPaymentMethod(e.target.value)}
              className="h-4 w-4 text-indigo-600 focus:ring-indigo-500"
            />
            <div>
              <span className="text-sm font-medium text-gray-900">Pay Online</span>
              <p className="text-xs text-gray-500">UPI, Cards, Netbanking via Razorpay</p>
            </div>
          </label>
        </div>
      </div>

      {/* Place order */}
      <button
        onClick={handlePlaceOrder}
        disabled={placing}
        className="w-full py-3 px-4 bg-indigo-600 text-white font-medium rounded-md hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {placing ? 'Placing order…' : 'Place Order'}
      </button>

      <Link to="/cart" className="block text-center mt-3 text-sm text-indigo-600 hover:text-indigo-500">
        ← Back to Cart
      </Link>
    </div>
  );
}
