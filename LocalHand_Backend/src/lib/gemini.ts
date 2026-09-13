import { GoogleGenAI } from "@google/genai";

export const GEMINI_MODEL = "gemini-3.6-flash";

let client: GoogleGenAI | null = null;

/**
 * Lazily constructed so a missing GEMINI_API_KEY only breaks the assistant
 * route when it's actually called, not the whole server at boot — every
 * other route works fine with no key configured.
 */
export function getGemini(): GoogleGenAI {
  if (client) return client;

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new Error("GEMINI_API_KEY is not set — copy .env.example to .env and fill it in.");
  }

  client = new GoogleGenAI({ apiKey });
  return client;
}
