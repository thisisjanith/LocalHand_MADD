-- Add phone as required, backfilling any existing rows with a placeholder
-- so the NOT NULL constraint can be applied, then drop the default so every
-- future insert must supply a real value (enforced by the signup route).
ALTER TABLE "users" ADD COLUMN "phone" TEXT NOT NULL DEFAULT '';
ALTER TABLE "users" ALTER COLUMN "phone" DROP DEFAULT;
