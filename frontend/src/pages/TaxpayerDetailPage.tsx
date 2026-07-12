import { useState } from "react";
import { useParams, Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { taxpayersApi } from "@/api/taxpayers";
import { filingsApi } from "@/api/filings";
import type { CreateFilingRequest, FilingResponse, FormType } from "@/types";
import StatusBadge from "@/components/StatusBadge";
import ActionGuidanceCard from "@/components/ActionGuidanceCard";
import Pagination from "@/components/Pagination";
import { ArrowLeft, Plus, Loader2, Calendar, Clock, ChevronDown, ChevronUp } from "lucide-react";

const FORM_TYPES: FormType[] = ["F1040", "F1120", "F1065", "F941"];
const PAGE_SIZE = 10;

const schema = z.object({
  formType: z.enum(["F1040", "F1120", "F1065", "F941"]),
  taxYear: z.string().regex(/^\d{4}$/, "4-digit year"),
  jurisdiction: z.string().min(1, "Required"),
  filingDate: z.string().min(1, "Required"),
});
type FormData = z.infer<typeof schema>;

export default function TaxpayerDetailPage() {
  const { taxpayerId } = useParams<{ taxpayerId: string }>();
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [expandedFiling, setExpandedFiling] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const { data: taxpayer } = useQuery({
    queryKey: ["taxpayer", taxpayerId],
    queryFn: () => taxpayersApi.get(taxpayerId!),
    enabled: !!taxpayerId,
  });

  const { data: filingsPage, isLoading } = useQuery({
    queryKey: ["filings", taxpayerId, page],
    queryFn: () => filingsApi.list(taxpayerId!, page, PAGE_SIZE),
    enabled: !!taxpayerId,
  });
  const filings = filingsPage?.content;

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { formType: "F1040", jurisdiction: "FEDERAL", taxYear: "2024" },
  });

  const mutation = useMutation({
    mutationFn: (body: CreateFilingRequest) => filingsApi.create(taxpayerId!, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["filings", taxpayerId] });
      reset();
      setShowForm(false);
    },
  });

  async function onSubmit(data: FormData) {
    await mutation.mutateAsync(data as CreateFilingRequest);
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/" className="text-gray-400 hover:text-gray-600 transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{taxpayer?.displayName ?? "Loading..."}</h1>
          <p className="text-sm text-gray-500">
            {taxpayer?.taxpayerType === "INDIVIDUAL" ? "Individual" : taxpayer?.entityType || "Business"}
            {taxpayer?.stateOfReg && ` · ${taxpayer.stateOfReg}`}
          </p>
        </div>
      </div>

      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-900">Filings</h2>
        <button
          onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Add Filing
        </button>
      </div>

      {showForm && (
        <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
          <h3 className="font-semibold text-gray-900 mb-4">Submit a filing</h3>
          <form onSubmit={handleSubmit(onSubmit)} className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Form Type</label>
              <select
                {...register("formType")}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {FORM_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Tax Year</label>
              <input
                {...register("taxYear")}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="2024"
                maxLength={4}
              />
              {errors.taxYear && <p className="text-xs text-red-500 mt-1">{errors.taxYear.message}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Jurisdiction</label>
              <input
                {...register("jurisdiction")}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="FEDERAL or state code, e.g. CA"
              />
              {errors.jurisdiction && <p className="text-xs text-red-500 mt-1">{errors.jurisdiction.message}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Filing Date</label>
              <input
                {...register("filingDate")}
                type="date"
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {errors.filingDate && <p className="text-xs text-red-500 mt-1">{errors.filingDate.message}</p>}
            </div>

            {mutation.isError && (
              <p className="col-span-2 text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">
                Failed to submit filing.
              </p>
            )}

            <div className="col-span-2 flex gap-3 justify-end">
              <button
                type="button"
                onClick={() => { setShowForm(false); reset(); }}
                className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={isSubmitting}
                className="flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60 transition-colors"
              >
                {isSubmitting && <Loader2 className="w-4 h-4 animate-spin" />}
                Submit
              </button>
            </div>
          </form>
        </div>
      )}

      {isLoading ? (
        <div className="flex justify-center py-12">
          <Loader2 className="w-6 h-6 animate-spin text-blue-600" />
        </div>
      ) : filings?.length === 0 ? (
        <div className="text-center py-16 text-gray-400 bg-white border border-gray-200 rounded-xl">
          <p className="text-sm">No filings yet. Submit one to start tracking your refund.</p>
        </div>
      ) : (
        <>
          <div className="space-y-3">
            {filings?.map((f) => (
              <FilingRow
                key={f.sk}
                filing={f}
                taxpayerId={taxpayerId!}
                expanded={expandedFiling === f.sk}
                onToggle={() => setExpandedFiling(expandedFiling === f.sk ? null : f.sk)}
              />
            ))}
          </div>
          {filingsPage && (
            <Pagination
              page={filingsPage.page}
              size={filingsPage.size}
              totalElements={filingsPage.totalElements}
              totalPages={filingsPage.totalPages}
              onPageChange={setPage}
            />
          )}
        </>
      )}
    </div>
  );
}

function FilingRow({ filing, taxpayerId, expanded, onToggle }: {
  filing: FilingResponse;
  taxpayerId: string;
  expanded: boolean;
  onToggle: () => void;
}) {
  const needsGuidance = filing.irsStatus === "FLAGGED" || filing.irsStatus === "UNDER_REVIEW";
  const { data: guidance } = useQuery({
    queryKey: ["guidance", taxpayerId, filing.sk],
    queryFn: () => filingsApi.guidance(taxpayerId, filing.taxYear, filing.formType, filing.jurisdiction),
    enabled: expanded && needsGuidance,
  });

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
      <button
        onClick={onToggle}
        className="w-full flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors text-left"
      >
        <div className="flex items-center gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="font-medium text-gray-900">{filing.formType}</span>
              <span className="text-gray-400">·</span>
              <span className="text-sm text-gray-600">{filing.taxYear}</span>
              <span className="text-gray-400">·</span>
              <span className="text-sm text-gray-600">{filing.jurisdiction}</span>
            </div>
            <p className="text-xs text-gray-400 mt-0.5">Filed {filing.filingDate}</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge status={filing.irsStatus} />
          {expanded ? <ChevronUp className="w-4 h-4 text-gray-400" /> : <ChevronDown className="w-4 h-4 text-gray-400" />}
        </div>
      </button>

      {expanded && (
        <div className="border-t border-gray-100 px-5 py-4 space-y-4">
          <div className="grid grid-cols-3 gap-4 text-sm">
            {filing.expectedDepositDate && (
              <div className="flex items-start gap-2">
                <Calendar className="w-4 h-4 text-gray-400 mt-0.5" />
                <div>
                  <p className="text-xs text-gray-500">Expected Deposit</p>
                  <p className="font-medium">{filing.expectedDepositDate}</p>
                </div>
              </div>
            )}
            {filing.aiPredictedDays != null && (
              <div className="flex items-start gap-2">
                <Clock className="w-4 h-4 text-gray-400 mt-0.5" />
                <div>
                  <p className="text-xs text-gray-500">AI Prediction</p>
                  <p className="font-medium">~{filing.aiPredictedDays} days
                    {filing.aiConfidence != null && (
                      <span className="text-gray-400 font-normal"> ({Math.round(filing.aiConfidence * 100)}% confidence)</span>
                    )}
                  </p>
                </div>
              </div>
            )}
            {filing.submissionId && (
              <div>
                <p className="text-xs text-gray-500">Submission ID</p>
                <p className="font-mono text-xs mt-0.5 text-gray-600 truncate">{filing.submissionId}</p>
              </div>
            )}
          </div>

          <ActionGuidanceCard status={filing.irsStatus} guidance={guidance} />

          {filing.statusHistory?.length > 0 && (
            <div>
              <p className="text-xs font-medium text-gray-500 mb-2">Status History</p>
              <ol className="relative border-l border-gray-200 space-y-2 ml-2">
                {filing.statusHistory.map((h, i) => (
                  <li key={i} className="ml-4">
                    <div className="absolute -left-1.5 w-3 h-3 rounded-full bg-blue-200 border border-blue-400" />
                    <div className="flex items-center gap-2">
                      <StatusBadge status={h.status} />
                      <span className="text-xs text-gray-400">{new Date(h.timestamp).toLocaleString()}</span>
                    </div>
                    {h.note && <p className="text-xs text-gray-500 mt-0.5">{h.note}</p>}
                  </li>
                ))}
              </ol>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
