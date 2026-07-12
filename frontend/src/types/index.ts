export type TaxpayerType = "INDIVIDUAL" | "BUSINESS";
export type FormType = "F1040" | "F1120" | "F1065" | "F941";
export type IrsStatus =
  | "RECEIVED"
  | "APPROVED"
  | "SENT"
  | "DEPOSITED"
  | "FLAGGED"
  | "UNDER_REVIEW";
export type AccessRole = "OWNER" | "DELEGATE";
export type AccountType = "INDIVIDUAL" | "CPA" | "BUSINESS_OWNER";

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  accountType: AccountType;
}

export interface TaxpayerResponse {
  id: string;
  taxpayerType: TaxpayerType;
  displayName: string;
  entityType: string | null;
  stateOfReg: string | null;
  createdAt: string;
}

export interface StatusHistoryEntry {
  status: IrsStatus;
  timestamp: string;
  note: string | null;
}

export interface FilingResponse {
  taxpayerId: string;
  sk: string;
  taxYear: string;
  formType: FormType;
  jurisdiction: string;
  irsStatus: IrsStatus;
  refundAmountEncrypted: string | null;
  filingDate: string;
  expectedDepositDate: string | null;
  submissionId: string | null;
  adapterUsed: string | null;
  aiPredictedDays: number | null;
  aiConfidence: number | null;
  aiModelVersion: string | null;
  lastSyncedAt: string | null;
  statusHistory: StatusHistoryEntry[];
}

export interface RegisterRequest {
  email: string;
  password: string;
  accountType: AccountType;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface CreateTaxpayerRequest {
  taxpayerType: TaxpayerType;
  taxId: string;
  displayName: string;
  entityType: string | null;
  stateOfReg: string | null;
}

export interface CreateFilingRequest {
  formType: FormType;
  taxYear: string;
  jurisdiction: string;
  filingDate: string;
}

export interface GuidanceDoc {
  id: number;
  topic: string;
  content: string;
  sourceUrl: string;
}

export interface GuidanceResponse {
  situationKey: string;
  narrative: string;
  sources: GuidanceDoc[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
