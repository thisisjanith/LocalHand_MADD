import { S3Client, PutObjectCommand, DeleteObjectCommand } from "@aws-sdk/client-s3";
import { randomUUID } from "crypto";

// R2 speaks the S3 API, so the standard AWS SDK works against it — just point
// the endpoint at Cloudflare's account-scoped R2 URL instead of AWS's.
const r2 = new S3Client({
  region: "auto",
  endpoint: `https://${process.env.R2_ACCOUNT_ID}.r2.cloudflarestorage.com`,
  credentials: {
    accessKeyId: process.env.R2_ACCESS_KEY_ID as string,
    secretAccessKey: process.env.R2_SECRET_ACCESS_KEY as string,
  },
});

const bucket = process.env.R2_BUCKET_NAME as string;
const publicUrl = (process.env.R2_PUBLIC_URL as string).replace(/\/$/, "");

/** Uploads a listing photo and returns its public URL. */
export async function uploadListingPhoto(
  buffer: Buffer,
  contentType: string,
): Promise<string> {
  const extension = contentType === "image/png" ? "png" : "jpg";
  const key = `listings/${randomUUID()}.${extension}`;

  await r2.send(
    new PutObjectCommand({
      Bucket: bucket,
      Key: key,
      Body: buffer,
      ContentType: contentType,
    }),
  );

  return `${publicUrl}/${key}`;
}

/** Deletes a previously uploaded photo given its public URL. */
export async function deleteListingPhoto(url: string): Promise<void> {
  if (!url.startsWith(publicUrl)) return; // not one of ours (or already gone)
  const key = url.slice(publicUrl.length + 1);
  await r2.send(new DeleteObjectCommand({ Bucket: bucket, Key: key }));
}
