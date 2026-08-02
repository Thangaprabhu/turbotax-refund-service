import type { GuidanceResponse, IrsStatus } from "@/types";
import { AlertTriangle, CheckCircle2, Clock, ExternalLink, Send } from "lucide-react";

interface GuidanceAction {
  label: string;
  href: string;
}

interface GuidanceConfig {
  icon: typeof AlertTriangle;
  tone: string;
  title: string;
  body: string;
  actions: GuidanceAction[];
  tip?: string;
}

const GUIDANCE: Partial<Record<IrsStatus, GuidanceConfig>> = {
  FLAGGED: {
    icon: AlertTriangle,
    tone: "border-red-200 bg-red-50 text-red-800",
    title: "This return needs attention",
    body: "The IRS has flagged this return for additional verification — most commonly identity verification. This isn't necessarily a sign of a problem with the return itself, but it will add time before your refund resumes processing. We don't have the specific reason here; check IRS.gov or any notice mailed to you for details.",
    actions: [
      { label: "Verify your identity with the IRS", href: "https://www.irs.gov/identity-theft-fraud-scams/identity-and-tax-return-verification-service" },
      { label: "Check status on IRS.gov", href: "https://www.irs.gov/wheres-my-refund" },
    ],
  },
  UNDER_REVIEW: {
    icon: Clock,
    tone: "border-yellow-200 bg-yellow-50 text-yellow-800",
    title: "Taking longer than the standard cycle",
    body: "Returns claiming certain credits (like EITC or the Additional Child Tax Credit) or selected for manual review can take significantly longer than the typical 21-day cycle. This is usually automatic and doesn't require any action from you.",
    actions: [
      { label: "Check status on IRS.gov", href: "https://www.irs.gov/wheres-my-refund" },
    ],
    tip: "If it's been more than 60 days past your original estimate, the IRS refund hotline is 800-829-1954.",
  },
  APPROVED: {
    icon: CheckCircle2,
    tone: "border-green-200 bg-green-50 text-green-800",
    title: "Refund approved",
    body: "The refund has been approved and is expected to be sent soon. In some cases the amount sent can differ from the original estimate — for example if a debt offset applies — so treat the official tracker as the source of truth once it's sent.",
    actions: [
      { label: "Check status on IRS.gov", href: "https://www.irs.gov/wheres-my-refund" },
    ],
  },
  SENT: {
    icon: Send,
    tone: "border-blue-200 bg-blue-50 text-blue-800",
    title: "Refund sent",
    body: "The refund has been sent. Direct deposit typically arrives within a few days; a mailed paper check can take one to several additional weeks. If it's been longer than that and the refund still hasn't arrived, a refund trace may be the next step.",
    actions: [
      { label: "Check status on IRS.gov", href: "https://www.irs.gov/wheres-my-refund" },
    ],
  },
};

interface Props {
  status: IrsStatus;
  /** RAG-retrieved guidance for this filing's specific situation. Undefined while
   *  loading or on fetch failure — falls back to the static body below either way. */
  guidance?: GuidanceResponse | null;
}

export default function ActionGuidanceCard({ status, guidance }: Props) {
  const config = GUIDANCE[status];
  if (!config) return null;

  const Icon = config.icon;
  const body = guidance?.narrative ?? config.body;

  return (
    <div className={`rounded-lg border px-4 py-3 space-y-2 ${config.tone}`}>
      <div className="flex items-start gap-2">
        <Icon className="w-4 h-4 mt-0.5 shrink-0" />
        <div className="space-y-1">
          <p className="text-sm font-semibold">{config.title}</p>
          <p className="text-sm opacity-90">{body}</p>
        </div>
      </div>
      <div className="flex flex-wrap gap-3 pl-6">
        {config.actions.map((action) => (
          <a
            key={action.href}
            href={action.href}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-sm font-medium underline underline-offset-2 hover:opacity-80"
          >
            {action.label}
            <ExternalLink className="w-3 h-3" />
          </a>
        ))}
      </div>
      {config.tip && <p className="text-xs opacity-75 pl-6">{config.tip}</p>}
      {guidance && guidance.sources.length > 0 && (
        <details className="pl-6 text-xs opacity-75">
          <summary className="cursor-pointer select-none">Sources ({guidance.sources.length})</summary>
          <ul className="mt-1 space-y-0.5 list-disc list-inside">
            {guidance.sources.map((s) => (
              <li key={s.id}>
                {s.sourceUrl ? (
                  <a href={s.sourceUrl} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2 hover:opacity-80">
                    {s.topic.replace(/_/g, " ")}
                  </a>
                ) : (
                  <span>{s.topic.replace(/_/g, " ")}</span>
                )}
                {s.simulated && (
                  <span className="ml-1.5 rounded-full bg-black/10 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide">
                    Simulated
                  </span>
                )}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
