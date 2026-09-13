import express from "express";
import cors from "cors";
import helmet from "helmet";
import morgan from "morgan";
import { authRouter } from "./routes/auth";
import { listingsRouter } from "./routes/listings";
import { usersRouter } from "./routes/users";
import { assistantRouter } from "./routes/assistant";
import { adminRouter } from "./routes/admin";

export function createApp() {
  const app = express();

  app.use(helmet());
  app.use(cors());
  app.use(morgan("dev"));
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  app.use("/auth", authRouter);
  app.use("/listings", listingsRouter);
  app.use("/users", usersRouter);
  app.use("/assistant", assistantRouter);
  app.use("/admin", adminRouter);

  // Multer file-filter rejections and any other thrown errors land here
  // rather than crashing the process or leaking a stack trace to clients.
  app.use(
    (err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
      console.error(err);
      res.status(400).json({ error: err.message || "Something went wrong" });
    },
  );

  return app;
}
