# Last session

- Dev API and frontend are deployed on Cloudflare; PlanetScale/Flyway/JDBC and password auth/session/CSRF passed hosted E2E.
- The frontend `/backend` gateway uses a Cloudflare service binding; public same-zone fetch caused error 1042.
- Local PostgreSQL + Mailpit registration mail passed E2E. Local ignored env files contain Google/ElevenLabs credentials.
- Runtime dev secrets are in Cloudflare; all one-time GitHub bootstrap secrets were deleted. Production remains gated.
- Google OAuth callback is runtime-configured through the hosted `/backend` gateway; a real hosted login landed authenticated at `/projects`. The active clients are the `*-current` pair.

## Next step

Run one dev transcription and record ElevenLabs cost/latency.
