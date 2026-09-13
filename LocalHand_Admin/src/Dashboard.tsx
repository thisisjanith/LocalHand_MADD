import { useEffect, useState } from "react";
import {
  ApiError,
  type AdminListing,
  type AdminUser,
  deleteListing,
  deleteUser,
  fetchListings,
  fetchUsers,
} from "./api";

type Tab = "users" | "listings";

export function Dashboard({ token, onLogOut }: { token: string; onLogOut: () => void }) {
  const [tab, setTab] = useState<Tab>("users");
  const [users, setUsers] = useState<AdminUser[] | null>(null);
  const [listings, setListings] = useState<AdminListing[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  async function load() {
    setIsLoading(true);
    setError(null);
    try {
      const [usersRes, listingsRes] = await Promise.all([fetchUsers(token), fetchListings(token)]);
      setUsers(usersRes.users);
      setListings(listingsRes.listings);
    } catch (err) {
      if (err instanceof ApiError && err.message.includes("admin token")) {
        onLogOut();
        return;
      }
      setError(err instanceof ApiError ? err.message : "Couldn't reach the server.");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleDeleteUser(id: string, name: string) {
    if (!confirm(`Delete ${name}'s account? This also removes their listings and favourites.`)) return;
    try {
      await deleteUser(token, id);
      setUsers((prev) => prev?.filter((u) => u.id !== id) ?? null);
      setListings((prev) => prev?.filter((l) => l.ownerEmail !== users?.find((u) => u.id === id)?.email) ?? null);
    } catch (err) {
      alert(err instanceof ApiError ? err.message : "Delete failed. Try again.");
    }
  }

  async function handleDeleteListing(id: string, title: string) {
    if (!confirm(`Delete listing "${title}"?`)) return;
    try {
      await deleteListing(token, id);
      setListings((prev) => prev?.filter((l) => l.id !== id) ?? null);
      setUsers((prev) =>
        prev?.map((u) => {
          const listing = listings?.find((l) => l.id === id);
          return listing && u.email === listing.ownerEmail
            ? { ...u, listingCount: u.listingCount - 1 }
            : u;
        }) ?? null,
      );
    } catch (err) {
      alert(err instanceof ApiError ? err.message : "Delete failed. Try again.");
    }
  }

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>LocalHand Admin</h1>
        <button className="link-button" onClick={onLogOut}>
          Sign out
        </button>
      </header>

      <nav className="tabs">
        <button className={tab === "users" ? "tab active" : "tab"} onClick={() => setTab("users")}>
          Users {users ? `(${users.length})` : ""}
        </button>
        <button className={tab === "listings" ? "tab active" : "tab"} onClick={() => setTab("listings")}>
          Listings {listings ? `(${listings.length})` : ""}
        </button>
        <button className="link-button refresh" onClick={load} disabled={isLoading}>
          {isLoading ? "Refreshing…" : "Refresh"}
        </button>
      </nav>

      {error && <p className="error-text">{error}</p>}

      {tab === "users" && (
        <UsersTable users={users} isLoading={isLoading} onDelete={handleDeleteUser} />
      )}
      {tab === "listings" && (
        <ListingsTable listings={listings} isLoading={isLoading} onDelete={handleDeleteListing} />
      )}
    </div>
  );
}

function UsersTable({
  users,
  isLoading,
  onDelete,
}: {
  users: AdminUser[] | null;
  isLoading: boolean;
  onDelete: (id: string, name: string) => void;
}) {
  if (isLoading && !users) return <p className="empty-state">Loading…</p>;
  if (!users || users.length === 0) return <p className="empty-state">No users yet.</p>;

  return (
    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th>Email</th>
          <th>Phone</th>
          <th>Locality</th>
          <th>Rating</th>
          <th>Listings</th>
          <th>Joined</th>
          <th />
        </tr>
      </thead>
      <tbody>
        {users.map((u) => (
          <tr key={u.id}>
            <td>{u.name}</td>
            <td>{u.email}</td>
            <td>{u.phone || "—"}</td>
            <td>{u.locality}</td>
            <td>{u.rating.toFixed(1)}★</td>
            <td>{u.listingCount}</td>
            <td>{new Date(u.createdAt).toLocaleDateString()}</td>
            <td>
              <button className="danger-button" onClick={() => onDelete(u.id, u.name)}>
                Delete
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ListingsTable({
  listings,
  isLoading,
  onDelete,
}: {
  listings: AdminListing[] | null;
  isLoading: boolean;
  onDelete: (id: string, title: string) => void;
}) {
  if (isLoading && !listings) return <p className="empty-state">Loading…</p>;
  if (!listings || listings.length === 0) return <p className="empty-state">No listings yet.</p>;

  return (
    <table>
      <thead>
        <tr>
          <th>Title</th>
          <th>Type</th>
          <th>Category</th>
          <th>Price</th>
          <th>Locality</th>
          <th>Owner</th>
          <th>Posted</th>
          <th />
        </tr>
      </thead>
      <tbody>
        {listings.map((l) => (
          <tr key={l.id}>
            <td>{l.title}</td>
            <td>{l.type === "SERVICE" ? "Service" : "Marketplace"}</td>
            <td>{l.category}</td>
            <td>{l.price}</td>
            <td>{l.locality}</td>
            <td>
              {l.ownerName}
              <div className="owner-email">{l.ownerEmail}</div>
            </td>
            <td>{new Date(l.createdAt).toLocaleDateString()}</td>
            <td>
              <button className="danger-button" onClick={() => onDelete(l.id, l.title)}>
                Delete
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
