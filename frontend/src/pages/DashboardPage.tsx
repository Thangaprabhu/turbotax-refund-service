import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { taxpayersApi } from "@/api/taxpayers";
import type { CreateTaxpayerRequest, TaxpayerType } from "@/types";
import { Plus, ChevronRight, Loader2, Building2, User } from "lucide-react";
import Pagination from "@/components/Pagination";

const PAGE_SIZE = 10;

const schema = z.object({
  taxpayerType: z.enum(["INDIVIDUAL", "BUSINESS"]),
  taxId: z
    .string()
    .regex(/^\d{3}-\d{2}-\d{4}$|^\d{2}-\d{7}$/, "SSN: 123-45-6789 or EIN: 12-3456789"),
  displayName: z.string().min(1, "Required"),
  entityType: z.string().optional(),
  stateOfReg: z.string().length(2, "2-letter state code").optional().or(z.literal("")),
});
type FormData = z.infer<typeof schema>;

export default function DashboardPage() {
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["taxpayers", page],
    queryFn: () => taxpayersApi.list(page, PAGE_SIZE),
  });
  const taxpayers = data?.content;

  const { register, handleSubmit, watch, reset, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { taxpayerType: "INDIVIDUAL" },
  });
  const taxpayerType = watch("taxpayerType");

  const mutation = useMutation({
    mutationFn: (body: CreateTaxpayerRequest) => taxpayersApi.create(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["taxpayers"] });
      reset();
      setShowForm(false);
    },
  });

  async function onSubmit(data: FormData) {
    await mutation.mutateAsync({
      taxpayerType: data.taxpayerType as TaxpayerType,
      taxId: data.taxId,
      displayName: data.displayName,
      entityType: data.entityType || null,
      stateOfReg: data.stateOfReg || null,
    });
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Taxpayers</h1>
          <p className="text-sm text-gray-500 mt-0.5">Track refund status for individuals and businesses</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Add Taxpayer
        </button>
      </div>

      {showForm && (
        <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
          <h2 className="font-semibold text-gray-900 mb-4">Register a taxpayer</h2>
          <form onSubmit={handleSubmit(onSubmit)} className="grid grid-cols-2 gap-4">
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Filer Type</label>
              <div className="flex gap-3">
                {(["INDIVIDUAL", "BUSINESS"] as const).map((type) => (
                  <label key={type} className="flex items-center gap-2 cursor-pointer">
                    <input type="radio" {...register("taxpayerType")} value={type} className="accent-blue-600" />
                    <span className="text-sm">{type === "INDIVIDUAL" ? "Individual (SSN)" : "Business (EIN)"}</span>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                {taxpayerType === "INDIVIDUAL" ? "SSN (123-45-6789)" : "EIN (12-3456789)"}
              </label>
              <input
                {...register("taxId")}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder={taxpayerType === "INDIVIDUAL" ? "123-45-6789" : "12-3456789"}
              />
              {errors.taxId && <p className="text-xs text-red-500 mt-1">{errors.taxId.message}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Display Name</label>
              <input
                {...register("displayName")}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Jane Doe"
              />
              {errors.displayName && <p className="text-xs text-red-500 mt-1">{errors.displayName.message}</p>}
            </div>

            {taxpayerType === "BUSINESS" && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Entity Type</label>
                <input
                  {...register("entityType")}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="LLC, S-Corp, Partnership..."
                />
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">State (optional)</label>
              <input
                {...register("stateOfReg")}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="CA"
                maxLength={2}
              />
              {errors.stateOfReg && <p className="text-xs text-red-500 mt-1">{errors.stateOfReg.message}</p>}
            </div>

            {mutation.isError && (
              <p className="col-span-2 text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">
                Failed to register taxpayer. Tax ID may already exist.
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
                Register
              </button>
            </div>
          </form>
        </div>
      )}

      {isLoading ? (
        <div className="flex justify-center py-12">
          <Loader2 className="w-6 h-6 animate-spin text-blue-600" />
        </div>
      ) : taxpayers?.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <FileIcon className="w-10 h-10 mx-auto mb-3 opacity-40" />
          <p className="text-sm">No taxpayers yet. Add one to get started.</p>
        </div>
      ) : (
        <>
          <div className="space-y-2">
            {taxpayers?.map((tp) => (
              <Link
                key={tp.id}
                to={`/taxpayers/${tp.id}`}
                className="flex items-center justify-between bg-white border border-gray-200 rounded-xl px-5 py-4 hover:border-blue-300 hover:shadow-sm transition-all group"
              >
                <div className="flex items-center gap-4">
                  <div className="w-9 h-9 rounded-full bg-blue-50 flex items-center justify-center text-blue-600">
                    {tp.taxpayerType === "INDIVIDUAL" ? <User className="w-4 h-4" /> : <Building2 className="w-4 h-4" />}
                  </div>
                  <div>
                    <p className="font-medium text-gray-900">{tp.displayName}</p>
                    <p className="text-xs text-gray-500">
                      {tp.taxpayerType === "INDIVIDUAL" ? "Individual" : tp.entityType || "Business"}
                      {tp.stateOfReg && ` · ${tp.stateOfReg}`}
                    </p>
                  </div>
                </div>
                <ChevronRight className="w-4 h-4 text-gray-400 group-hover:text-blue-500 transition-colors" />
              </Link>
            ))}
          </div>
          {data && (
            <Pagination
              page={data.page}
              size={data.size}
              totalElements={data.totalElements}
              totalPages={data.totalPages}
              onPageChange={setPage}
            />
          )}
        </>
      )}
    </div>
  );
}

function FileIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
        d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  );
}
