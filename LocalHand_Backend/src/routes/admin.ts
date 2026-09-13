import { Router } from "express";
import { z } from "zod";
import { prisma } from "../lib/prisma";
import { signAdminToken } from "../lib/jwt";
import { requireAdmin } from "../middleware/auth";
import { deleteListingPhoto } from "../lib/r2";

export const adminRouter = Router();

// Express 5's route-param type is `string | string[]`; every :id here is a
// single path segment, so this narrows it back to the plain string every
// call site actually wants.
function paramId(value: string | string[]): string {
  return Array.isArray(value) ? value[0] : value;
}

const loginSchema = z.object({
  email: z.string().trim().toLowerCase(),
  password: z.string(),
});

/**
 * A single hardcoded admin credential from env vars, not a User row — the
 * dashboard is a one-person operations tool, not part of the app's own
 * multi-user account system.
 */
adminRouter.post("/login", (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: "Invalid input" });
    return;
  }
  const { email, password } = parsed.data;

  const adminEmail = process.env.ADMIN_EMAIL?.toLowerCase();
  const adminPassword = process.env.ADMIN_PASSWORD;

  if (!adminEmail || !adminPassword || email !== adminEmail || password !== adminPassword) {
    res.status(401).json({ error: "Incorrect email or password" });
    return;
  }

  res.json({ token: signAdminToken() });
});

/** GET /admin/users — every registered user, most recent first. */
adminRouter.get("/users", requireAdmin, async (_req, res) => {
  const users = await prisma.user.findMany({
    orderBy: { createdAt: "desc" },
    include: { _count: { select: { listings: true } } },
  });

  res.json({
    users: users.map((u) => ({
      id: u.id,
      name: u.name,
      email: u.email,
      phone: u.phone,
      locality: u.locality,
      rating: u.rating,
      memberSince: u.memberSince,
      createdAt: u.createdAt.getTime(),
      listingCount: u._count.listings,
    })),
  });
});

/** DELETE /admin/users/:id — cascades to their listings and favourites. */
adminRouter.delete("/users/:id", requireAdmin, async (req, res) => {
  const id = paramId(req.params.id);
  const user = await prisma.user.findUnique({
    where: { id },
    include: { listings: { select: { imageUrls: true } } },
  });
  if (!user) {
    res.status(404).json({ error: "User not found" });
    return;
  }

  // Clean up R2 storage for every photo this user's listings held, before
  // the cascading delete removes the DB rows that reference them.
  for (const listing of user.listings) {
    await Promise.all(listing.imageUrls.map((url) => deleteListingPhoto(url)));
  }

  await prisma.user.delete({ where: { id } });
  res.status(204).send();
});

/** GET /admin/listings — every listing regardless of owner, most recent first. */
adminRouter.get("/listings", requireAdmin, async (_req, res) => {
  const listings = await prisma.listing.findMany({
    orderBy: { createdAt: "desc" },
    include: { owner: { select: { name: true, email: true } } },
  });

  res.json({
    listings: listings.map((l) => ({
      id: l.id,
      type: l.type,
      title: l.title,
      category: l.category,
      price: l.price,
      locality: l.locality,
      description: l.description,
      imageUrls: l.imageUrls,
      createdAt: l.createdAt.getTime(),
      ownerName: l.owner.name,
      ownerEmail: l.owner.email,
    })),
  });
});

/** DELETE /admin/listings/:id — bypasses the owner-only check regular users hit. */
adminRouter.delete("/listings/:id", requireAdmin, async (req, res) => {
  const id = paramId(req.params.id);
  const existing = await prisma.listing.findUnique({ where: { id } });
  if (!existing) {
    res.status(404).json({ error: "Listing not found" });
    return;
  }

  await Promise.all(existing.imageUrls.map((url) => deleteListingPhoto(url)));
  await prisma.listing.delete({ where: { id } });
  res.status(204).send();
});
