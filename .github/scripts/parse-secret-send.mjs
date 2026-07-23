export function parseSecretSend(sendText) {
  const supplied = new Map();
  const text = sendText.trim();

  try {
    const parsed = JSON.parse(text);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      for (const [key, value] of Object.entries(parsed)) {
        if (typeof value === "string") supplied.set(key.trim(), value.trim());
      }
    }
  } catch {
    // Human-readable Sends may use dotenv or YAML-style separators.
  }

  if (supplied.size === 0) {
    for (const line of text.split(/\r?\n/)) {
      const match = line.match(
        /^\s*(?:[-*]\s*)?(?:export\s+)?([A-Z][A-Z0-9_]*)\s*(?:=|:)\s*(.*?)\s*$/,
      );
      if (!match) continue;
      const value = match[2].replace(/^(["'])(.*)\1$/, "$2").trim();
      supplied.set(match[1], value);
    }
  }

  return supplied;
}
