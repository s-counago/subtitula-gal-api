# Last session

- Dev API and frontend are deployed on Cloudflare; PlanetScale/Flyway/JDBC and password auth/session/CSRF passed hosted E2E.
- The frontend `/backend` gateway uses a Cloudflare service binding; public same-zone fetch caused error 1042.
- Local PostgreSQL + Mailpit registration mail passed E2E. Local ignored env files contain Google/ElevenLabs credentials.
- Runtime dev secrets are in Cloudflare; all one-time GitHub bootstrap secrets were deleted. Production remains gated.

## Next step

Run one real dev transcription and record ElevenLabs cost/latency; then route Google OAuth through `/backend`.
