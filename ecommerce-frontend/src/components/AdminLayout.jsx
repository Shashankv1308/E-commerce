import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import useAuth from '../hooks/useAuth';

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

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="min-h-screen bg-gray-50 flex">
      {/* Sidebar */}
      <aside className="w-56 bg-white border-r border-gray-200 flex flex-col">
        <div className="px-4 py-4 border-b border-gray-200">
          <NavLink to="/products" className="text-lg font-bold text-indigo-600 tracking-tight">
            ShopEase
          </NavLink>
          <span className="block text-xs text-gray-500 mt-0.5">Admin Panel</span>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1">
          {sideLinks.map((link) => (
            <NavLink key={link.to} to={link.to} className={sideNavClass}>
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
      <div className="flex-1 flex flex-col">
        <header className="bg-white border-b border-gray-200 px-6 py-3">
          <h2 className="text-lg font-semibold text-gray-800">Admin Dashboard</h2>
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
