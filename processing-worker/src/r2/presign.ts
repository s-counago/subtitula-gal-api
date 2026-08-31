import {
  GetObjectCommand,
  PutObjectCommand,
  S3Client,
} from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import type { ProcessingEnv } from "../types";

function client(env: ProcessingEnv): S3Client {
  if (
    !env.R2_S3_ENDPOINT.startsWith("https://")
    || !env.R2_S3_ACCESS_KEY_ID
    || !env.R2_S3_SECRET_ACCESS_KEY
  ) {
    throw new Error("r2_presigning_not_configured");
  }
  return new S3Client({
    region: "auto",
    endpoint: env.R2_S3_ENDPOINT,
    credentials: {
      accessKeyId: env.R2_S3_ACCESS_KEY_ID,
      secretAccessKey: env.R2_S3_SECRET_ACCESS_KEY,
    },
  });
}

export function canPresign(env: ProcessingEnv): boolean {
  return env.R2_S3_ENDPOINT.startsWith("https://")
    && Boolean(env.R2_S3_ACCESS_KEY_ID)
    && Boolean(env.R2_S3_SECRET_ACCESS_KEY);
}

export function presignUpload(
  env: ProcessingEnv,
  objectKey: string,
  mimeType: string,
  expiresIn: number,
): Promise<string> {
  return getSignedUrl(
    client(env),
    new PutObjectCommand({
      Bucket: env.R2_BUCKET_NAME,
      Key: objectKey,
      ContentType: mimeType,
    }),
    { expiresIn },
  );
}

export function presignProviderRead(
  env: ProcessingEnv,
  objectKey: string,
  expiresIn: number,
): Promise<string> {
  return getSignedUrl(
    client(env),
    new GetObjectCommand({
      Bucket: env.R2_BUCKET_NAME,
      Key: objectKey,
    }),
    { expiresIn },
  );
}
