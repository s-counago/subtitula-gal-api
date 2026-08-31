import { hmacSha256, hex, sha256 } from "./crypto";

export async function internalSignatureHeaders(
  secret: string,
  method: string,
  canonicalPath: string,
  body: string,
  now = new Date(),
  nonce = crypto.randomUUID(),
): Promise<Headers> {
  const timestamp = Math.floor(now.getTime() / 1000).toString();
  const digest = await sha256(body);
  const canonical = [
    method.toUpperCase(),
    canonicalPath,
    timestamp,
    nonce,
    digest,
  ].join("\n");
  const signature = hex(await hmacSha256(secret, canonical));
  return new Headers({
    "content-type": "application/json",
    "x-subtitula-timestamp": timestamp,
    "x-subtitula-nonce": nonce,
    "x-subtitula-content-sha256": digest,
    "x-subtitula-signature": signature,
  });
}
