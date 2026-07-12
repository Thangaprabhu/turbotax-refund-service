import type { IrsStatus } from "@/types";

const config: Record<IrsStatus, { label: string; className: string }> = {
  RECEIVED:     { label: "Received",     className: "bg-blue-100 text-blue-700" },
  APPROVED:     { label: "Approved",     className: "bg-green-100 text-green-700" },
  SENT:         { label: "Sent",         className: "bg-purple-100 text-purple-700" },
  DEPOSITED:    { label: "Deposited",    className: "bg-emerald-100 text-emerald-700" },
  FLAGGED:      { label: "Flagged",      className: "bg-red-100 text-red-700" },
  UNDER_REVIEW: { label: "Under Review", className: "bg-yellow-100 text-yellow-700" },
};

export default function StatusBadge({ status }: { status: IrsStatus }) {
  const { label, className } = config[status] ?? { label: status, className: "bg-gray-100 text-gray-700" };
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}
