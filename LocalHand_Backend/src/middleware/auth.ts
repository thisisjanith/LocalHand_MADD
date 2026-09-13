import { Request, Response, NextFunction } from "express";
import { verifyAccessToken, verifyAdminToken } from "../lib/jwt";

// Augment Express's Request type so req.userId is visible to route handlers
// without a cast at every call site.
declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      userId?: string;
    }
  }
}

/** Requires a valid "Authorization: Bearer <token>" header; 401s otherwise. */
export function requireAuth(req: Request, res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  const token = header?.startsWith("Bearer ") ? header.slice("Bearer ".length) : null;

  if (!token) {
    res.status(401).json({ error: "Missing bearer token" });
    return;
  }

  try {
    const payload = verifyAccessToken(token);
    req.userId = payload.userId;
    next();
  } catch {
    res.status(401).json({ error: "Invalid or expired token" });
  }
}

/**
 * Same as requireAuth, but doesn't reject the request when no/invalid token
 * is present — just leaves req.userId unset. Used on routes like listing
 * search, where knowing "is this the caller's own listing" is nice to have
 * but anonymous browsing is still allowed.
 */
export function optionalAuth(req: Request, _res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  const token = header?.startsWith("Bearer ") ? header.slice("Bearer ".length) : null;
  if (token) {
    try {
      req.userId = verifyAccessToken(token).userId;
    } catch {
      // Invalid token on an optional route: proceed unauthenticated rather
      // than reject, since the route doesn't require identity to function.
    }
  }
  next();
}

/**
 * Requires a valid admin token (from POST /admin/login) — a separate token
 * type from regular user auth, so a normal user's token never passes here.
 */
export function requireAdmin(req: Request, res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  const token = header?.startsWith("Bearer ") ? header.slice("Bearer ".length) : null;

  if (!token) {
    res.status(401).json({ error: "Missing bearer token" });
    return;
  }

  try {
    verifyAdminToken(token);
    next();
  } catch {
    res.status(401).json({ error: "Invalid or expired admin token" });
  }
}
