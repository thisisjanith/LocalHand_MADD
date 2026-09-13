import { Router } from "express";
import multer from "multer";
import { z } from "zod";
import { prisma } from "../lib/prisma";
import { requireAuth, optionalAuth } from "../middleware/auth";
import { uploadListingPhoto, deleteListingPhoto } from "../lib/r2";

// Express 5's route-param type is `string | string[]` (repeated segments are
// merged into an array); every :id here is a single path segment, so this
// narrows it back to the plain string every call site actually wants.
function paramId(value: string | string[]): string {
  return Array.isArray(value) ? value[0] : value;
}

/** favouritedBy filtered to the caller — [] both when logged out and when not favourited. */
function favouritedByFilter(callerId?: string) {
  return { where: { userId: callerId ?? "" } };
}

export const listingsRouter = Router();

const MAX_PHOTOS = 5;

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 8 * 1024 * 1024, files: MAX_PHOTOS }, // 8MB each, up to 5 files
  fileFilter: (_req, file, cb) => {
    if (file.mimetype === "image/jpeg" || file.mimetype === "image/png") {
      cb(null, true);
    } else {
      cb(new Error("Only JPEG or PNG photos are allowed"));
    }
  },
});

const ListingType = z.enum(["SERVICE", "MARKETPLACE"]);
const Category = z.enum([
  "REPAIRS",
  "TUTORING",
  "CLEANING",
  "GARDEN",
  "ERRANDS",
  "ELECTRONICS",
  "FURNITURE",
  "OTHER",
]);
const Condition = z.enum(["LIKE_NEW", "GOOD", "FAIR"]);

const listingBodySchema = z.object({
  type: ListingType,
  title: z.string().trim().min(1).max(200),
  category: Category,
  price: z.string().trim().min(1).max(60),
  locality: z.string().trim().min(1).max(120),
  latitude: z.coerce.number().min(-90).max(90).nullable().optional(),
  longitude: z.coerce.number().min(-180).max(180).nullable().optional(),
  condition: Condition.nullable().optional(),
  description: z.string().trim().min(1).max(4000),
});

/** GET /listings — search/browse. Supports type, category, ownerId and q (keyword) filters. */
listingsRouter.get("/", optionalAuth, async (req, res) => {
  const typeParam = ListingType.safeParse(req.query.type);
  const categoryParam = Category.safeParse(req.query.category);
  const q = typeof req.query.q === "string" ? req.query.q.trim() : undefined;
  const ownerId = typeof req.query.ownerId === "string" ? req.query.ownerId : undefined;

  const listings = await prisma.listing.findMany({
    where: {
      type: typeParam.success ? typeParam.data : undefined,
      category: categoryParam.success ? categoryParam.data : undefined,
      ownerId,
      ...(q
        ? {
            OR: [
              { title: { contains: q, mode: "insensitive" } },
              { description: { contains: q, mode: "insensitive" } },
              { locality: { contains: q, mode: "insensitive" } },
            ],
          }
        : {}),
    },
    orderBy: { createdAt: "desc" },
    include: { owner: true, favouritedBy: favouritedByFilter(req.userId) },
  });

  res.json({ listings: listings.map((l) => toPublicListing(l, req.userId)) });
});

/** GET /listings/mine — the signed-in user's own listings. */
listingsRouter.get("/mine", requireAuth, async (req, res) => {
  const listings = await prisma.listing.findMany({
    where: { ownerId: req.userId },
    orderBy: { createdAt: "desc" },
    include: { owner: true, favouritedBy: favouritedByFilter(req.userId) },
  });
  res.json({ listings: listings.map((l) => toPublicListing(l, req.userId)) });
});

/** GET /listings/favourites — listings the signed-in user has favourited. */
listingsRouter.get("/favourites", requireAuth, async (req, res) => {
  const favourites = await prisma.favourite.findMany({
    where: { userId: req.userId },
    orderBy: { createdAt: "desc" },
    include: { listing: { include: { owner: true, favouritedBy: favouritedByFilter(req.userId) } } },
  });
  res.json({ listings: favourites.map((f) => toPublicListing(f.listing, req.userId)) });
});

/** GET /listings/best?category=X — the top-rated provider in a category, for the assistant. */
listingsRouter.get("/best", optionalAuth, async (req, res) => {
  const categoryParam = Category.safeParse(req.query.category);
  if (!categoryParam.success) {
    res.status(400).json({ error: "category query param must be a valid category" });
    return;
  }
  const listing = await prisma.listing.findFirst({
    where: { category: categoryParam.data },
    orderBy: { owner: { rating: "desc" } },
    include: { owner: true, favouritedBy: favouritedByFilter(req.userId) },
  });
  res.json({ listing: listing ? toPublicListing(listing, req.userId) : null });
});

/** GET /listings/:id */
listingsRouter.get("/:id", optionalAuth, async (req, res) => {
  const listing = await prisma.listing.findUnique({
    where: { id: paramId(req.params.id) },
    include: { owner: true, favouritedBy: favouritedByFilter(req.userId) },
  });
  if (!listing) {
    res.status(404).json({ error: "Listing not found" });
    return;
  }
  res.json({ listing: toPublicListing(listing, req.userId) });
});

/** POST /listings — create, with up to 5 multipart "photo" fields. */
listingsRouter.post("/", requireAuth, upload.array("photo", MAX_PHOTOS), async (req, res) => {
  const parsed = listingBodySchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.issues[0]?.message ?? "Invalid input" });
    return;
  }
  const data = parsed.data;

  const files = (req.files as Express.Multer.File[] | undefined) ?? [];
  const imageUrls = await Promise.all(
    files.map((file) => uploadListingPhoto(file.buffer, file.mimetype)),
  );

  const listing = await prisma.listing.create({
    data: {
      ...data,
      imageUrls,
      ownerId: req.userId as string,
    },
    include: { owner: true, favouritedBy: favouritedByFilter(req.userId) },
  });

  res.status(201).json({ listing: toPublicListing(listing, req.userId) });
});

/**
 * PUT /listings/:id — edit; owner only. Final photo set = the URLs named in
 * "existingImageUrls" (a JSON array, only the ones the client wants to keep)
 * plus any newly uploaded "photo" files, capped at MAX_PHOTOS total. Photos
 * dropped from "existingImageUrls" are deleted from R2, not just unlinked.
 */
listingsRouter.put("/:id", requireAuth, upload.array("photo", MAX_PHOTOS), async (req, res) => {
  const existing = await prisma.listing.findUnique({ where: { id: paramId(req.params.id) } });
  if (!existing) {
    res.status(404).json({ error: "Listing not found" });
    return;
  }
  if (existing.ownerId !== req.userId) {
    res.status(403).json({ error: "You can only edit your own listings" });
    return;
  }

  const parsed = listingBodySchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.issues[0]?.message ?? "Invalid input" });
    return;
  }
  const data = parsed.data;

  const keptUrls = z
    .array(z.string())
    .catch([])
    .parse(
      typeof req.body.existingImageUrls === "string"
        ? JSON.parse(req.body.existingImageUrls)
        : req.body.existingImageUrls,
    )
    .filter((url) => existing.imageUrls.includes(url));

  const removedUrls = existing.imageUrls.filter((url) => !keptUrls.includes(url));
  await Promise.all(removedUrls.map((url) => deleteListingPhoto(url)));

  const files = (req.files as Express.Multer.File[] | undefined) ?? [];
  const newUrls = await Promise.all(
    files
      .slice(0, Math.max(0, MAX_PHOTOS - keptUrls.length))
      .map((file) => uploadListingPhoto(file.buffer, file.mimetype)),
  );

  const listing = await prisma.listing.update({
    where: { id: existing.id },
    data: { ...data, imageUrls: [...keptUrls, ...newUrls] },
    include: { owner: true, favouritedBy: favouritedByFilter(req.userId) },
  });

  res.json({ listing: toPublicListing(listing, req.userId) });
});

/** DELETE /listings/:id — owner only. */
listingsRouter.delete("/:id", requireAuth, async (req, res) => {
  const existing = await prisma.listing.findUnique({ where: { id: paramId(req.params.id) } });
  if (!existing) {
    res.status(404).json({ error: "Listing not found" });
    return;
  }
  if (existing.ownerId !== req.userId) {
    res.status(403).json({ error: "You can only delete your own listings" });
    return;
  }

  await Promise.all(existing.imageUrls.map((url) => deleteListingPhoto(url)));
  await prisma.listing.delete({ where: { id: existing.id } });
  res.status(204).send();
});

/** POST /listings/:id/favourite — toggle on. */
listingsRouter.post("/:id/favourite", requireAuth, async (req, res) => {
  const listing = await prisma.listing.findUnique({ where: { id: paramId(req.params.id) } });
  if (!listing) {
    res.status(404).json({ error: "Listing not found" });
    return;
  }
  await prisma.favourite.upsert({
    where: { userId_listingId: { userId: req.userId as string, listingId: listing.id } },
    create: { userId: req.userId as string, listingId: listing.id },
    update: {},
  });
  res.status(204).send();
});

/** DELETE /listings/:id/favourite — toggle off. */
listingsRouter.delete("/:id/favourite", requireAuth, async (req, res) => {
  await prisma.favourite
    .delete({
      where: { userId_listingId: { userId: req.userId as string, listingId: paramId(req.params.id) } },
    })
    .catch(() => {
      // Already not favourited — deleting a favourite twice is a no-op, not an error.
    });
  res.status(204).send();
});

type ListingWithOwner = {
  id: string;
  type: string;
  title: string;
  category: string;
  price: string;
  locality: string;
  latitude: number | null;
  longitude: number | null;
  condition: string | null;
  description: string;
  imageUrls: string[];
  createdAt: Date;
  ownerId: string;
  owner: { id: string; name: string; phone: string; rating: number };
  favouritedBy: { userId: string }[];
};

/**
 * Shapes a Prisma listing into what the Android app expects: flattened
 * provider name/rating (from the owner relation, not duplicated per-listing),
 * isMine relative to the caller, and isFavourite from the join-table lookup
 * rather than a stored boolean.
 */
function toPublicListing(listing: ListingWithOwner, callerId?: string) {
  return {
    id: listing.id,
    type: listing.type,
    title: listing.title,
    category: listing.category,
    providerId: listing.ownerId,
    providerName: listing.owner.name,
    providerPhone: listing.owner.phone,
    rating: listing.owner.rating,
    price: listing.price,
    locality: listing.locality,
    latitude: listing.latitude,
    longitude: listing.longitude,
    condition: listing.condition,
    description: listing.description,
    imageUrls: listing.imageUrls,
    isMine: callerId != null && listing.ownerId === callerId,
    isFavourite: listing.favouritedBy.length > 0,
    createdAt: listing.createdAt.getTime(),
  };
}
