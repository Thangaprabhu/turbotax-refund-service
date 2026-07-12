import client from "./client";
import type { CreateTaxpayerRequest, PageResponse, TaxpayerResponse } from "@/types";

export const taxpayersApi = {
  list: (page = 0, size = 10) =>
    client
      .get<PageResponse<TaxpayerResponse>>("/taxpayers", { params: { page, size } })
      .then((r) => r.data),

  get: (id: string) =>
    client.get<TaxpayerResponse>(`/taxpayers/${id}`).then((r) => r.data),

  create: (body: CreateTaxpayerRequest) =>
    client.post<TaxpayerResponse>("/taxpayers", body).then((r) => r.data),
};
