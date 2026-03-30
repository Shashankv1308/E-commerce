import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import useAuth from '../hooks/useAuth';
import { useState } from 'react';

const navLinkClass = ({ isActive }) =>
  `px-3 py-2 rounded-md text-sm font-medium ${
    isActive
      ? 'bg-indigo-700 text-white'
      : 'text-indigo-100 hover:bg-indigo-500 hover:text-white'
  }`;

export default function Layout() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-indigo-600 shadow-md">
        <div className="max-w-7xl mx-auto px-4">
          <div className="flex items-center justify-between h-14">
            {/* Logo */}
            <Link to="/products" className="text-white font-bold text-lg tracking-tight">
              ShopEase
            </Link>

            {/* Desktop nav links */}
            <div className="hidden md:flex items-center space-x-2">
              <NavLink to="/products" className={navLinkClass}>
                Products
              </NavLink>
              <NavLink to="/cart" className={navLinkClass}>
                Cart
              </NavLink>
              <NavLink to="/orders" className={navLinkClass}>
                Orders
              </NavLink>
              {isAdmin && (
                <NavLink to="/admin/orders" className={navLinkClass}>
                  Admin
                </NavLink>
              )}
            </div>

            {/* User info + logout (desktop) */}
            <div className="hidden md:flex items-center space-x-3">
              <span className="text-indigo-100 text-sm">{user?.email}</span>
              <button
                onClick={handleLogout}
                className="px-3 py-1.5 text-sm font-medium text-indigo-100 border border-indigo-400 rounded-md hover:bg-indigo-500 hover:text-white"
              >
                Logout
              </button>
            </div>

            {/* Mobile hamburger */}
            <button
              className="md:hidden text-indigo-100 hover:text-white"
              onClick={() => setMenuOpen(!menuOpen)}
              aria-label="Toggle menu"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                {menuOpen ? (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                )}
              </svg>
            </button>
          </div>

          {/* Mobile menu */}
          {menuOpen && (
            <div className="md:hidden pb-3 space-y-1">
              <NavLink to="/products" className={navLinkClass} onClick={() => setMenuOpen(false)}>
                Products
              </NavLink>
              <NavLink to="/cart" className={navLinkClass} onClick={() => setMenuOpen(false)}>
                Cart
              </NavLink>
              <NavLink to="/orders" className={navLinkClass} onClick={() => setMenuOpen(false)}>
                Orders
              </NavLink>
              {isAdmin && (
                <NavLink to="/admin/orders" className={navLinkClass} onClick={() => setMenuOpen(false)}>
                  Admin
                </NavLink>
              )}
              <div className="pt-2 border-t border-indigo-500">
                <span className="block px-3 py-1 text-indigo-200 text-sm">{user?.email}</span>
                <button
                  onClick={() => { setMenuOpen(false); handleLogout(); }}
                  className="block w-full text-left px-3 py-2 text-sm text-indigo-100 hover:bg-indigo-500 rounded-md"
                >
                  Logout
                </button>
              </div>
            </div>
          )}
        </div>
      </nav>

      {/* Page content */}
      <main className="max-w-7xl mx-auto px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
