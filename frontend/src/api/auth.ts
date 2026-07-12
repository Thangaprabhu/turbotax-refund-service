import client from "./client";
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/types";

export const authApi = {
  register: (body: RegisterRequest) =>
    client.post<AuthResponse>("/auth/register", body).then((r) => r.data),

  login: (body: LoginRequest) =>
    client.post<AuthResponse>("/auth/login", body).then((r) => r.data),
};
