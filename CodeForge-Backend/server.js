require("dotenv").config();
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const rateLimit = require("express-rate-limit");

const app = express();
app.disable("x-powered-by");
app.use(helmet());
app.use(cors());
app.use(express.json({ limit: "100kb" }));

const runLimiter = rateLimit({
  windowMs: 60_000,
  limit: 10,
  standardHeaders: "draft-7",
  legacyHeaders: false
});

const COMPILERS = {
  python: process.env.ONLINECOMPILER_PYTHON_COMPILER || "python-3.13",
  php: process.env.ONLINECOMPILER_PHP_COMPILER || "php-8.4",
  cpp: process.env.ONLINECOMPILER_CPP_COMPILER || "cpp-17",
  c: process.env.ONLINECOMPILER_C_COMPILER || "c-17"
};

app.get("/health", (_req, res) => res.json({ ok: true, service: "CodeForge OnlineCompiler.io Backend" }));

app.post("/api/run", runLimiter, async (req, res) => {
  const { compiler, code, input = "" } = req.body || {};
  const allowedCompilers = new Set(Object.keys(COMPILERS));
  if (typeof code !== "string" || !code.trim()) {
    return res.status(400).json({ error: "Code is required." });
  }
  if (Buffer.byteLength(code, "utf8") > 50_000) {
    return res.status(413).json({ error: "Code is too large (50 KB maximum)." });
  }
  if (typeof input !== "string" || Buffer.byteLength(input, "utf8") > 50_000) {
    return res.status(400).json({ error: "Invalid program input." });
  }
  if (typeof compiler !== "string" || !allowedCompilers.has(compiler.toLowerCase())) {
    return res.status(400).json({ error: "Unsupported language. Use python, php, cpp, or c." });
  }
  const apiKey = process.env.ONLINECOMPILER_API_KEY;
  if (!apiKey) return res.status(500).json({ error: "Backend is missing ONLINECOMPILER_API_KEY." });

  try {
    const upstream = await fetch("https://api.onlinecompiler.io/api/run-code-sync/", {
      method: "POST",
      headers: {
        Authorization: apiKey,
        "Content-Type": "application/json",
        Accept: "application/json"
      },
      body: JSON.stringify({ compiler: COMPILERS[compiler.toLowerCase()], code, input, time_limit: 10 }),
      signal: AbortSignal.timeout(45_000)
    });
    const raw = await upstream.text();
    let data;
    try { data = JSON.parse(raw); } catch { data = { output: raw }; }
    if (!upstream.ok) {
      console.error("OnlineCompiler.io HTTP status:", upstream.status);
      return res.status(502).json({
        error: data.error || data.message || "OnlineCompiler.io could not run this code. Check your API key, compiler ID, and usage limit."
      });
    }
    // Normalize the response so the Android app can show output and errors consistently.
    return res.json({
      output: data.output ?? data.stdout ?? data.result ?? "",
      error: data.error ?? data.stderr ?? data.compile_output ?? "",
      status: data.status ?? data.status_description ?? "Finished",
      runtime: data.runtime ?? data.time ?? null,
      memory: data.memory ?? null
    });
  } catch (err) {
    console.error("OnlineCompiler.io request failed:", err.message);
    return res.status(502).json({ error: "Could not contact OnlineCompiler.io. Check the server connection and try again." });
  }
});

app.use((err, _req, res, _next) => {
  if (err instanceof SyntaxError && "body" in err) return res.status(400).json({ error: "Invalid JSON." });
  console.error("Backend error:", err.message);
  return res.status(500).json({ error: "Internal server error." });
});

const port = Number(process.env.PORT || 3000);
app.listen(port, "0.0.0.0", () => console.log(`CodeForge backend listening on port ${port}`));
