import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api/axios';
import formatCurrency from '../utils/formatCurrency';

export default function Cart() {
  const navigate = useNavigate();
  const [cart, setCart] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [removingId, setRemovingId] = useState(null);

  function fetchCart() {
    setLoading(true);
    api
      .get('/cart')
      .then((res) => setCart(res.data))
      .catch(() => setError('Failed to load cart.'))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    fetchCart();
  }, []);

  async function handleRemove(productId) {
    setRemovingId(productId);
    try {
      const res = await api.delete(`/cart/items/${productId}`);
      setCart(res.data);
    } catch {
      setError('Failed to remove item.');
    } finally {
      setRemovingId(null);
    }
  }

  if (loading) {
    return (
      <div className="animate-pulse space-y-4">
        <div className="h-7 bg-gray-200 rounded w-24" />
        <div className="h-10 bg-gray-200 rounded" />
        <div className="h-10 bg-gray-200 rounded" />
        <div className="h-10 bg-gray-200 rounded" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-md">
        {error}
      </div>
    );
  }

  const items = cart?.items || [];
  const isEmpty = items.length === 0;

  if (isEmpty) {
    return (
      <div className="text-center py-16">
        <svg className="mx-auto h-16 w-16 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 100 4 2 2 0 000-4z" />
        </svg>
        <h2 className="mt-4 text-lg font-semibold text-gray-900">Your cart is empty</h2>
        <p className="mt-1 text-sm text-gray-500">Browse products and add something you like.</p>
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
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Shopping Cart</h1>

      {/* Desktop table */}
      <div className="hidden sm:block bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <table className="w-full text-left">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-5 py-3 text-xs font-medium text-gray-500 uppercase">Product</th>
              <th className="px-5 py-3 text-xs font-medium text-gray-500 uppercase">Price</th>
              <th className="px-5 py-3 text-xs font-medium text-gray-500 uppercase">Qty</th>
              <th className="px-5 py-3 text-xs font-medium text-gray-500 uppercase">Subtotal</th>
              <th className="px-5 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {items.map((item) => (
              <tr key={item.productId}>
                <td className="px-5 py-4 text-sm font-medium text-gray-900">{item.productName}</td>
                <td className="px-5 py-4 text-sm text-gray-600">{formatCurrency(item.price)}</td>
                <td className="px-5 py-4 text-sm text-gray-600">{item.quantity}</td>
                <td className="px-5 py-4 text-sm font-medium text-gray-900">{formatCurrency(item.subtotal)}</td>
                <td className="px-5 py-4 text-right">
                  <button
                    onClick={() => handleRemove(item.productId)}
                    disabled={removingId === item.productId}
                    className="text-red-600 hover:text-red-800 text-sm font-medium disabled:opacity-50"
                  >
                    {removingId === item.productId ? 'Removing…' : 'Remove'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile cards */}
      <div className="sm:hidden space-y-3">
        {items.map((item) => (
          <div key={item.productId} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <div className="flex justify-between items-start">
              <div>
                <p className="font-medium text-gray-900">{item.productName}</p>
                <p className="text-sm text-gray-500 mt-1">
                  {formatCurrency(item.price)} × {item.quantity}
                </p>
              </div>
              <p className="font-medium text-gray-900">{formatCurrency(item.subtotal)}</p>
            </div>
            <button
              onClick={() => handleRemove(item.productId)}
              disabled={removingId === item.productId}
              className="mt-2 text-red-600 hover:text-red-800 text-sm font-medium disabled:opacity-50"
            >
              {removingId === item.productId ? 'Removing…' : 'Remove'}
            </button>
          </div>
        ))}
      </div>

      {/* Total + Checkout */}
      <div className="mt-6 flex flex-col sm:flex-row items-center justify-between gap-4 bg-white rounded-lg shadow-sm border border-gray-200 p-5">
        <p className="text-lg font-semibold text-gray-900">
          Total: <span className="text-green-700">{formatCurrency(cart.totalAmount)}</span>
        </p>
        <button
          onClick={() => navigate('/checkout')}
          className="w-full sm:w-auto px-6 py-2.5 bg-indigo-600 text-white font-medium rounded-md hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        >
          Proceed to Checkout
        </button>
      </div>
    </div>
  );
}
