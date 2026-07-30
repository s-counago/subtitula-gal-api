import { base64Url, constantTimeEqual, fromBase64Url, hmacSha256 } from "./crypto";

export type UploadTokenPayload = {
  intentId: string;
  projectId: string;
  objectKey: string;
  sizeBytes: number;
  mimeType: string;
  expiresAtEpochSec: number;
};

export async function createUploadToken(
  secret: string,
  payload: UploadTokenPayload,
): Promise<string> {
  const encoded = base64Url(new TextEncoder().encode(JSON.stringify(payload)));
  const signature = base64Url(await hmacSha256(secret, encoded));
  return `${encoded}.${signature}`;
}

export async function verifyUploadToken(
  secret: string,
  token: string,
  nowEpochSec = Math.floor(Date.now() / 1000),
): Promise<UploadTokenPayload | null> {
  const [encoded, claimedSignature, extra] = token.split(".");
  if (!encoded || !claimedSignature || extra) return null;
  let actualSignature: Uint8Array;
  let claimedBytes: Uint8Array;
  try {
    actualSignature = await hmacSha256(secret, encoded);
    claimedBytes = fromBase64Url(claimedSignature);
  } catch {
    return null;
  }
  if (!constantTimeEqual(actualSignature, claimedBytes)) return null;
  try {
    const payload = JSON.parse(
      new TextDecoder().decode(fromBase64Url(encoded)),
    ) as UploadTokenPayload;
    if (
      typeof payload.intentId !== "string"
      || typeof payload.projectId !== "string"
      || typeof payload.objectKey !== "string"
      || typeof payload.sizeBytes !== "number"
      || typeof payload.mimeType !== "string"
      || typeof payload.expiresAtEpochSec !== "number"
      || payload.expiresAtEpochSec < nowEpochSec
    ) return null;
    return payload;
  } catch {
    return null;
  }
}
