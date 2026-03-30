const colorMap = {
  // Order statuses
  CONFIRMED: 'bg-blue-100 text-blue-800',
  SHIPPED: 'bg-yellow-100 text-yellow-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
  // Payment statuses
  SUCCESS: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  AWAITING_PAYMENT: 'bg-orange-100 text-orange-800',
  PENDING: 'bg-yellow-100 text-yellow-800',
  REFUNDED: 'bg-purple-100 text-purple-800',
};

export default function StatusBadge({ status }) {
  const colors = colorMap[status] || 'bg-gray-100 text-gray-800';
  const label = status?.replace(/_/g, ' ') || '—';

  return (
    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-semibold uppercase ${colors}`}>
      {label}
    </span>
  );
}
