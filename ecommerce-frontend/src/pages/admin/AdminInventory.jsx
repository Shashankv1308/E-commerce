import { useEffect, useState, useCallback } from 'react';
import api from '../../api/axios';
import Pagination from '../../components/Pagination';

export default function AdminInventory() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // inline edit state
  const [editId, setEditId] = useState(null);
  const [adjType, setAdjType] = useState('DELTA');
  const [adjQty, setAdjQty] = useState('');
  const [updating, setUpdating] = useState(false);
  const [updateMsg, setUpdateMsg] = useState('');

  const fetchInventory = useCallback(() => {
    setLoading(true);
    setError('');
    api
      .get('/admin/inventory', { params: { page, size: 15, sort: 'productId,asc' } })
      .then((res) => {
        setItems(res.data.content);
        setTotalPages(res.data.totalPages);
      })
      .catch(() => setError('Failed to load inventory.'))
      .finally(() => setLoading(false));
  }, [page]);

  useEffect(() => { fetchInventory(); }, [fetchInventory]);

  function startEdit(item) {
    setEditId(item.productId);
    setAdjType('DELTA');
    setAdjQty('');
    setUpdateMsg('');
  }

  function cancelEdit() {
    setEditId(null);
    setUpdateMsg('');
  }

  function saveEdit(productId) {
    const qty = parseInt(adjQty, 10);
    if (isNaN(qty)) { setUpdateMsg('Enter a valid number.'); return; }
    setUpdating(true);
    setUpdateMsg('');
    api
      .patch(`/admin/inventory/${productId}`, { adjustmentType: adjType, quantity: qty })
      .then((res) => {
        setItems((prev) => prev.map((it) => (it.productId === productId ? res.data : it)));
        setEditId(null);
        setUpdateMsg('Stock updated.');
      })
      .catch((err) => setUpdateMsg(err.response?.data?.message || 'Update failed.'))
      .finally(() => setUpdating(false));
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Inventory</h1>

      {updateMsg && !editId && (
        <div className={`p-3 mb-4 rounded-md text-sm ${updateMsg.includes('updated') ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'}`}>{updateMsg}</div>
      )}

      {error && <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-md mb-4">{error}</div>}

      {loading && (
        <div className="animate-pulse space-y-3">
          {Array.from({ length: 6 }).map((_, i) => <div key={i} className="h-12 bg-gray-200 rounded" />)}
        </div>
      )}

      {!loading && items.length === 0 && !error && (
        <p className="text-gray-500 py-8 text-center">No inventory records found.</p>
      )}

      {!loading && items.length > 0 && (
        <>
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-x-auto">
            <table className="w-full text-left min-w-[650px]">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Product ID</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Product</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Available</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Total</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Active</th>
                  <th className="px-4 py-3 text-xs font-medium text-gray-500 uppercase">Adjust</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {items.map((item) => (
                  <tr key={item.productId} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-sm font-medium text-gray-900">#{item.productId}</td>
                    <td className="px-4 py-3 text-sm text-gray-700">{item.productName}</td>
                    <td className="px-4 py-3 text-sm font-medium text-gray-900">{item.availableStock}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{item.totalStock}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-block w-2 h-2 rounded-full ${item.isActive ? 'bg-green-500' : 'bg-red-400'}`} />
                      <span className="ml-2 text-sm">{item.isActive ? 'Yes' : 'No'}</span>
                    </td>
                    <td className="px-4 py-3">
                      {editId === item.productId ? (
                        <div className="flex flex-wrap items-center gap-2">
                          <select
                            value={adjType}
                            onChange={(e) => setAdjType(e.target.value)}
                            className="px-2 py-1 text-sm border border-gray-300 rounded-md"
                          >
                            <option value="DELTA">Delta</option>
                            <option value="ABSOLUTE">Absolute</option>
                          </select>
                          <input
                            type="number"
                            placeholder="Qty"
                            value={adjQty}
                            onChange={(e) => setAdjQty(e.target.value)}
                            className="w-20 px-2 py-1 text-sm border border-gray-300 rounded-md"
                          />
                          <button
                            onClick={() => saveEdit(item.productId)}
                            disabled={updating}
                            className="px-3 py-1 text-xs bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:opacity-50"
                          >
                            {updating ? '…' : 'Save'}
                          </button>
                          <button onClick={cancelEdit} className="px-3 py-1 text-xs bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300">
                            Cancel
                          </button>
                          {updateMsg && editId === item.productId && (
                            <p className="text-xs text-red-600 w-full">{updateMsg}</p>
                          )}
                        </div>
                      ) : (
                        <button
                          onClick={() => startEdit(item)}
                          className="px-3 py-1 text-xs border border-gray-300 rounded-md hover:bg-gray-100"
                        >
                          Adjust
                        </button>
                      )}
                    </td>
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
