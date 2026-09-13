-- Replace the single imageUrl column with an ordered array, preserving any
-- existing photo as the sole entry in its listing's new array rather than
-- discarding it.
ALTER TABLE "listings" ADD COLUMN "imageUrls" TEXT[] NOT NULL DEFAULT '{}';

UPDATE "listings"
SET "imageUrls" = ARRAY["imageUrl"]
WHERE "imageUrl" IS NOT NULL;

ALTER TABLE "listings" DROP COLUMN "imageUrl";
