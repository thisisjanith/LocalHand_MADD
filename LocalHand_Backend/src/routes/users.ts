import { Router } from "express";
import { z } from "zod";
import { prisma } from "../lib/prisma";
import { requireAuth } from "../middleware/auth";

export const usersRouter = Router();

/**
 * GET /users/:id — another user's public profile, shown when tapping a
 * listing's provider. Deliberately excludes email/phone (only the listing
 * itself, via toPublicListing, exposes a phone — for contacting about that
 * specific listing, not as a general directory lookup).
 */
usersRouter.get("/:id", async (req, res) => {
  const user = await prisma.user.findUnique({ where: { id: req.params.id } });
  if (!user) {
    res.status(404).json({ error: "User not found" });
    return;
  }

  res.json({
    user: {
      id: user.id,
      name: user.name,
      locality: user.locality,
      rating: user.rating,
      memberSince: user.memberSince,
    },
  });
});

const updateLocationSchema = z.object({
  locality: z.string().trim().min(1).max(120),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
});

/** PUT /users/me/location — re-anchor the pin driving "near you" ranking. */
usersRouter.put("/me/location", requireAuth, async (req, res) => {
  const parsed = updateLocationSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.issues[0]?.message ?? "Invalid input" });
    return;
  }

  const user = await prisma.user.update({
    where: { id: req.userId },
    data: parsed.data,
  });

  res.json({
    user: {
      id: user.id,
      name: user.name,
      email: user.email,
      phone: user.phone,
      locality: user.locality,
      latitude: user.latitude,
      longitude: user.longitude,
      rating: user.rating,
      memberSince: user.memberSince,
    },
  });
});
