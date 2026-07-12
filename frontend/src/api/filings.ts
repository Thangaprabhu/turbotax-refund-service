import client from "./client";
import type { CreateFilingRequest, FilingResponse, GuidanceResponse, PageResponse } from "@/types";

export const filingsApi = {
  list: (taxpayerId: string, page = 0, size = 10) =>
    client
      .get<PageResponse<FilingResponse>>(`/taxpayers/${taxpayerId}/filings`, { params: { page, size } })
      .then((r) => r.data),

  latest: (taxpayerId: string) =>
    client
      .get<FilingResponse>(`/taxpayers/${taxpayerId}/filings/latest`)
      .then((r) => r.data),

  create: (taxpayerId: string, body: CreateFilingRequest) =>
    client
      .post<FilingResponse>(`/taxpayers/${taxpayerId}/filings`, body)
      .then((r) => r.data),

  guidance: (taxpayerId: string, taxYear: string, formType: string, jurisdiction: string) =>
    client
      .get<GuidanceResponse>(`/taxpayers/${taxpayerId}/filings/${taxYear}/${formType}/${jurisdiction}/guidance`)
      .then((r) => (r.status === 204 ? null : r.data)),
};
