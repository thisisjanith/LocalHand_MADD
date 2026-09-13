import { PrismaClient } from "@prisma/client";

// A single shared client across the app — re-instantiating PrismaClient per
// request exhausts Postgres connections under load.
export const prisma = new PrismaClient();
