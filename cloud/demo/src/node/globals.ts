// Node globals the simulation expects. Imported first in the worker.
import { Buffer } from "./buffer.ts";

(globalThis as { Buffer?: unknown }).Buffer = Buffer;
