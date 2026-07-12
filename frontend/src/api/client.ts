import axios from "axios";
import { useAuthStore } from "@/store/auth";

const client = axios.create({ baseURL: "/api/v1" });

client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

client.interceptors.response.use(
  (r) => r,
  (err) => {
    // A 401 from the login/register endpoints themselves is an expected, recoverable
    // validation failure (wrong password, duplicate email) that the calling page already
    // handles and displays inline -- redirecting here would wipe that message off screen
    // before the user ever saw it. Only treat a 401 elsewhere as "your session expired."
    const isAuthEndpoint = /\/auth\/(login|register)$/.test(err.config?.url ?? "");
    if (err.response?.status === 401 && !isAuthEndpoint) {
      useAuthStore.getState().logout();
      window.location.href = "/login";
    }
    return Promise.reject(err);
  }
);

export default client;
