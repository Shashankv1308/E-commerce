import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import useAuth from '../hooks/useAuth';
import { useState } from 'react';

const sideLinks = [
  { to: '/admin/orders', label: 'Orders' },
  { to: '/admin/payments', label: 'Payments' },
  { to: '/admin/inventory', label: 'Inventory' },
  { to: '/admin/audit-logs', label: 'Audit Logs' },
  { to: '/admin/create-admin', label: 'Create Admin' },
];

const sideNavClass = ({ isActive }) =>
  `block px-4 py-2 rounded-md text-sm font-medium ${
    isActive
      ? 'bg-indigo-50 text-indigo-700'
      : 'text-gray-700 hover:bg-gray-100'
  }`;

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="min-h-screen bg-gray-50 flex">
      {/* Mobile backdrop */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-30 bg-black/40 md:hidden" onClick={() => setSidebarOpen(false)} />
      )}

      {/* Sidebar */}
      <aside className={`fixed inset-y-0 left-0 z-40 w-56 bg-white border-r border-gray-200 flex flex-col transform transition-transform duration-200 md:relative md:translate-x-0 ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="px-4 py-4 border-b border-gray-200 flex items-center justify-between">
          <div>
            <NavLink to="/products" className="text-lg font-bold text-indigo-600 tracking-tight">
              ShopEase
            </NavLink>
            <span className="block text-xs text-gray-500 mt-0.5">Admin Panel</span>
          </div>
          <button className="md:hidden text-gray-500 hover:text-gray-700" onClick={() => setSidebarOpen(false)} aria-label="Close sidebar">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1">
          {sideLinks.map((link) => (
            <NavLink key={link.to} to={link.to} className={sideNavClass} onClick={() => setSidebarOpen(false)}>
              {link.label}
            </NavLink>
          ))}
        </nav>

        <div className="px-4 py-3 border-t border-gray-200">
          <p className="text-sm text-gray-600 truncate">{user?.email}</p>
          <div className="mt-2 flex space-x-2">
            <NavLink
              to="/products"
              className="text-xs text-indigo-600 hover:text-indigo-500 font-medium"
            >
              Store
            </NavLink>
            <button
              onClick={handleLogout}
              className="text-xs text-red-600 hover:text-red-500 font-medium"
            >
              Logout
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex-1 flex flex-col min-w-0">
        <header className="bg-white border-b border-gray-200 px-4 md:px-6 py-3 flex items-center gap-3">
          <button className="md:hidden text-gray-600 hover:text-gray-800" onClick={() => setSidebarOpen(true)} aria-label="Open sidebar">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <h2 className="text-lg font-semibold text-gray-800">Admin Dashboard</h2>
        </header>
        <main className="flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
