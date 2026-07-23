# Last session

- PlanetScale `subtitula` is Cloudflare-billed: branch `development`, PS-5 single-node Belgium, role `subtitula_app_dev`.
- The role’s initial password was discarded; reset it directly into Bitwarden/Worker Secrets before deployment.
- Production DB/domain/email remain deliberately unprovisioned.
- Dev deployment tokens and Google/ElevenLabs local+dev credentials are already in Bitwarden.

## Next step

Implement Wrangler/OpenNext + Container manifests, reset/install the dev DB secret, deploy dev and run hosted E2E. Create isolated HA production only at launch.
