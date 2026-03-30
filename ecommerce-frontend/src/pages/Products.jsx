import { useEffect, useState } from 'react';
import api from '../api/axios';
import formatCurrency from '../utils/formatCurrency';

function SkeletonCard() {
  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5 animate-pulse">
      <div className="h-5 bg-gray-200 rounded w-3/4 mb-3" />
      <div className="h-3 bg-gray-200 rounded w-full mb-2" />
      <div className="h-3 bg-gray-200 rounded w-2/3 mb-4" />
      <div className="h-6 bg-gray-200 rounded w-1/3 mb-2" />
      <div className="h-3 bg-gray-200 rounded w-1/4 mb-4" />
      <div className="h-9 bg-gray-200 rounded w-full" />
    </div>
  );
}

export default function Products() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [addingId, setAddingId] = useState(null);
  const [toast, setToast] = useState('');

  useEffect(() => {
    api
      .get('/products')
      .then((res) => setProducts(res.data))
      .catch(() => setError('Failed to load products.'))
      .finally(() => setLoading(false));
  }, []);

  async function handleAddToCart(productId) {
    setAddingId(productId);
    try {
      await api.post('/cart/items', { productId, quantity: 1 });
      setToast('Added to cart');
      setTimeout(() => setToast(''), 2000);
    } catch (err) {
      setToast(err.response?.data?.message || err.response?.data || 'Could not add to cart');
      setTimeout(() => setToast(''), 3000);
    } finally {
      setAddingId(null);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Products</h1>

      {/* Toast */}
      {toast && (
        <div className="fixed top-4 right-4 z-50 bg-gray-900 text-white px-4 py-2 rounded-md shadow-lg text-sm">
          {toast}
        </div>
      )}

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-md">
          {error}
        </div>
      )}

      {/* Loading skeletons */}
      {loading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
          {Array.from({ length: 8 }).map((_, i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
      )}

      {/* Product grid */}
      {!loading && !error && products.length === 0 && (
        <p className="text-gray-500">No products available right now.</p>
      )}

      {!loading && products.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
          {products.map((product) => {
            const outOfStock = product.availableStock <= 0;
            return (
              <div
                key={product.id}
                className={`bg-white rounded-lg shadow-sm border border-gray-200 p-5 flex flex-col ${
                  outOfStock ? 'opacity-60' : ''
                }`}
              >
                <h2 className="text-lg font-semibold text-gray-900 mb-1">
                  {product.name}
                </h2>
                <p className="text-sm text-gray-500 mb-3 line-clamp-2">
                  {product.description || 'No description'}
                </p>

                <p className="text-xl font-bold text-green-700 mb-1">
                  {formatCurrency(product.price)}
                </p>
                <p className={`text-xs mb-4 ${outOfStock ? 'text-red-500 font-medium' : 'text-gray-400'}`}>
                  {outOfStock ? 'Out of stock' : `${product.availableStock} in stock`}
                </p>

                <button
                  onClick={() => handleAddToCart(product.id)}
                  disabled={outOfStock || addingId === product.id}
                  className="mt-auto w-full py-2 px-4 text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-gray-300 disabled:cursor-not-allowed disabled:text-gray-500"
                >
                  {addingId === product.id ? 'Adding…' : 'Add to Cart'}
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
