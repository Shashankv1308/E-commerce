import { useState } from 'react';
import api from '../../api/axios';

export default function CreateAdmin() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [isError, setIsError] = useState(false);

  function handleSubmit(e) {
    e.preventDefault();
    if (!email || !password) return;
    if (password.length < 6) {
      setMessage('Password must be at least 6 characters.');
      setIsError(true);
      return;
    }

    setLoading(true);
    setMessage('');
    const body = { email, password };
    if (phone.trim()) body.phoneNumber = phone.trim();

    api
      .post('/admin/users', body)
      .then((res) => {
        setMessage(res.data.message || 'Admin created successfully.');
        setIsError(false);
        setEmail('');
        setPassword('');
        setPhone('');
      })
      .catch((err) => {
        setMessage(err.response?.data?.message || 'Failed to create admin.');
        setIsError(true);
      })
      .finally(() => setLoading(false));
  }

  const inputClass = 'w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500';

  return (
    <div className="max-w-md">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Create Admin User</h1>

      {message && (
        <div className={`p-3 mb-4 rounded-md text-sm border ${isError ? 'bg-red-50 text-red-700 border-red-200' : 'bg-green-50 text-green-700 border-green-200'}`}>
          {message}
        </div>
      )}

      <form onSubmit={handleSubmit} className="bg-white border border-gray-200 rounded-lg p-6 space-y-4">
        <div>
          <label htmlFor="admin-email" className="block text-sm font-medium text-gray-700 mb-1">Email</label>
          <input
            id="admin-email"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={inputClass}
            placeholder="admin@example.com"
          />
        </div>

        <div>
          <label htmlFor="admin-password" className="block text-sm font-medium text-gray-700 mb-1">Password</label>
          <input
            id="admin-password"
            type="password"
            required
            minLength={6}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={inputClass}
            placeholder="Min 6 characters"
          />
        </div>

        <div>
          <label htmlFor="admin-phone" className="block text-sm font-medium text-gray-700 mb-1">Phone (optional)</label>
          <input
            id="admin-phone"
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            className={inputClass}
            placeholder="+91 9876543210"
          />
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full py-2 px-4 bg-indigo-600 text-white font-medium rounded-md hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? 'Creating…' : 'Create Admin'}
        </button>
      </form>
    </div>
  );
}
