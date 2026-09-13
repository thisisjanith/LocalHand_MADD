import { Router } from "express";
import { z } from "zod";
import { Type, type Content, type FunctionDeclaration } from "@google/genai";
import { prisma } from "../lib/prisma";
import { getGemini, GEMINI_MODEL } from "../lib/gemini";
import { requireAuth } from "../middleware/auth";

export const assistantRouter = Router();

const CATEGORIES = [
  "REPAIRS",
  "TUTORING",
  "CLEANING",
  "GARDEN",
  "ERRANDS",
  "ELECTRONICS",
  "FURNITURE",
  "OTHER",
] as const;

const chatSchema = z.object({
  message: z.string().trim().min(1).max(1000),
  // Prior turns, oldest first — kept client-side and replayed each request
  // since this route is stateless (no server-side conversation storage).
  history: z
    .array(
      z.object({
        fromUser: z.boolean(),
        text: z.string(),
      }),
    )
    .max(30)
    .default([]),
});

const searchListingsDeclaration: FunctionDeclaration = {
  name: "search_listings",
  description:
    "Search real LocalHand listings (services or marketplace items) posted by neighbours. " +
    "Use this whenever the user is asking to find, hire, or buy something specific, so you " +
    "recommend a real listing instead of a generic answer. Results are ordered by the " +
    "provider's rating, best first.",
  parameters: {
    type: Type.OBJECT,
    properties: {
      category: {
        type: Type.STRING,
        enum: [...CATEGORIES],
        description: "Narrow to one category if the request clearly matches one.",
      },
      keyword: {
        type: Type.STRING,
        description: "A keyword to match against listing titles and descriptions.",
      },
    },
  },
};

const recommendListingDeclaration: FunctionDeclaration = {
  name: "recommend_listing",
  description:
    "Call this once you've decided a specific listing from your search_listings results is " +
    "genuinely worth recommending to the user, so the app can link them straight to it. Only " +
    "call this with an id that came from a prior search_listings result — never guess an id. " +
    "Don't call this if none of the results are actually a good match.",
  parameters: {
    type: Type.OBJECT,
    properties: {
      listingId: {
        type: Type.STRING,
        description: "The id of the listing to recommend, taken from a search_listings result.",
      },
    },
    required: ["listingId"],
  },
};

/** Shared with GET /listings — same shape, minus the caller-relative fields. */
async function searchListings(args: { category?: string; keyword?: string }) {
  const category = CATEGORIES.includes(args.category as (typeof CATEGORIES)[number])
    ? (args.category as (typeof CATEGORIES)[number])
    : undefined;

  const listings = await prisma.listing.findMany({
    where: {
      category,
      ...(args.keyword
        ? {
            OR: [
              { title: { contains: args.keyword, mode: "insensitive" } },
              { description: { contains: args.keyword, mode: "insensitive" } },
            ],
          }
        : {}),
    },
    orderBy: { owner: { rating: "desc" } },
    take: 5,
    include: { owner: { select: { name: true, rating: true } } },
  });

  return listings.map((l) => ({
    id: l.id,
    title: l.title,
    category: l.category,
    price: l.price,
    locality: l.locality,
    providerName: l.owner.name,
    rating: l.owner.rating,
  }));
}

const SYSTEM_INSTRUCTION =
  "You are the LocalHand assistant, built into a hyper-local neighbourhood services and " +
  "marketplace app. Help users understand how the app works and find real neighbours who can " +
  "help them. When someone asks to find, hire, or buy something, call search_listings rather " +
  "than guessing — never invent a provider name, price, or listing that didn't come from the " +
  "tool. Judge the results yourself: if one is a genuinely good match, call recommend_listing " +
  "with its id before your final reply. If none of the results actually fit the request (e.g. " +
  "they're placeholder or unrelated listings), don't call recommend_listing — just say plainly " +
  "that you couldn't find a good match and suggest posting a request of their own. Keep replies " +
  "short — two or three sentences — like a text message, not an essay. The app's own " +
  "mechanics: tap the green + button in the bottom nav to post a Service or Marketplace " +
  "listing; tap the heart on a listing to favourite it (view saved items in Profile > " +
  "Favourites); contact a poster directly via the Call or WhatsApp buttons on their listing — " +
  "there is no in-app inbox; Search tab filters by type, category and keyword; Dark Mode is a " +
  "toggle in Profile.";

assistantRouter.post("/chat", requireAuth, async (req, res) => {
  const parsed = chatSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.issues[0]?.message ?? "Invalid input" });
    return;
  }
  const { message, history } = parsed.data;

  const contents: Content[] = [
    ...history.map((turn) => ({
      role: turn.fromUser ? "user" : "model",
      parts: [{ text: turn.text }],
    })),
    { role: "user", parts: [{ text: message }] },
  ];

  try {
    const gemini = getGemini();
    let referencedListingId: string | undefined;
    // Ids the model has actually seen via search_listings — recommend_listing
    // is only honoured if it names one of these, never a guessed id.
    const seenListingIds = new Set<string>();

    // Bounded loop rather than one fixed round-trip: the model can search,
    // recommend, and still call another tool before it has enough to answer
    // in text (e.g. search again with a different keyword).
    const MAX_TOOL_ROUNDS = 4;
    let response;
    for (let round = 0; round <= MAX_TOOL_ROUNDS; round++) {
      response = await gemini.models.generateContent({
        model: GEMINI_MODEL,
        contents,
        config: {
          systemInstruction: SYSTEM_INSTRUCTION,
          tools: [{ functionDeclarations: [searchListingsDeclaration, recommendListingDeclaration] }],
        },
      });

      const calls = response.functionCalls;
      if (!calls || calls.length === 0) break;

      // Replay the model's own turn verbatim (not a hand-built { functionCall }
      // part) — newer models attach a thoughtSignature to the function-call
      // part that must round-trip unchanged, or the next call 400s.
      const modelTurn = response.candidates?.[0]?.content;
      if (modelTurn) contents.push(modelTurn);

      for (const call of calls) {
        let result: unknown;
        if (call.name === "recommend_listing") {
          const id = (call.args as { listingId?: string }).listingId;
          const valid = id != null && seenListingIds.has(id);
          if (valid) referencedListingId = id;
          result = { acknowledged: valid };
        } else {
          const results = await searchListings(call.args as { category?: string; keyword?: string });
          results.forEach((r) => seenListingIds.add(r.id));
          result = { results };
        }

        contents.push({
          role: "user",
          parts: [{ functionResponse: { name: call.name, response: result as Record<string, unknown> } }],
        });
      }
    }

    const reply = response?.text?.trim();
    if (!reply) {
      res.status(502).json({ error: "The assistant didn't return a reply. Try again." });
      return;
    }

    res.json({ reply, listingId: referencedListingId ?? null });
  } catch (err) {
    console.error("Gemini request failed:", err);
    res.status(502).json({ error: "The assistant is unavailable right now. Try again shortly." });
  }
});
