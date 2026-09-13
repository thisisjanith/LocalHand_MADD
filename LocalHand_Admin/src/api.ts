const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:3000";

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  phone: string;
  locality: string;
  rating: number;
  memberSince: number;
  createdAt: number;
  listingCount: number;
}

export interface AdminListing {
  id: string;
  type: "SERVICE" | "MARKETPLACE";
  title: string;
  category: string;
  price: string;
  locality: string;
  description: string;
  imageUrls: string[];
  createdAt: number;
  ownerName: string;
  ownerEmail: string;
}

export class ApiError extends Error {}

async function request<T>(path: string, token: string | null, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(body?.error ?? `Request failed (${response.status})`);
  }

  if (response.status === 204) return undefined as T;
  return response.json();
}

export function login(email: string, password: string): Promise<{ token: string }> {
  return request("/admin/login", null, {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function fetchUsers(token: string): Promise<{ users: AdminUser[] }> {
  return request("/admin/users", token);
}

export function deleteUser(token: string, id: string): Promise<void> {
  return request(`/admin/users/${id}`, token, { method: "DELETE" });
}

export function fetchListings(token: string): Promise<{ listings: AdminListing[] }> {
  return request("/admin/listings", token);
}

export function deleteListing(token: string, id: string): Promise<void> {
  return request(`/admin/listings/${id}`, token, { method: "DELETE" });
}
