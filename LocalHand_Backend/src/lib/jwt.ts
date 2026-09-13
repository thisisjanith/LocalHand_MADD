import jwt from "jsonwebtoken";

const secret = process.env.JWT_SECRET;
if (!secret) {
  throw new Error("JWT_SECRET is not set — copy .env.example to .env and fill it in.");
}

export interface AccessTokenPayload {
  userId: string;
}

export function signAccessToken(payload: AccessTokenPayload): string {
  return jwt.sign(payload, secret as string, {
    expiresIn: (process.env.JWT_EXPIRES_IN ?? "7d") as jwt.SignOptions["expiresIn"],
  });
}

export function verifyAccessToken(token: string): AccessTokenPayload {
  return jwt.verify(token, secret as string) as AccessTokenPayload;
}

export interface AdminTokenPayload {
  isAdmin: true;
}

// A short-lived, separate token type from regular user auth — the admin
// dashboard isn't a User row, just an env-configured credential, so it gets
// its own signing/verifying pair rather than overloading AccessTokenPayload.
export function signAdminToken(): string {
  return jwt.sign({ isAdmin: true } satisfies AdminTokenPayload, secret as string, {
    expiresIn: "12h",
  });
}

export function verifyAdminToken(token: string): AdminTokenPayload {
  const payload = jwt.verify(token, secret as string) as Partial<AdminTokenPayload>;
  if (payload.isAdmin !== true) {
    throw new Error("Not an admin token");
  }
  return payload as AdminTokenPayload;
}
