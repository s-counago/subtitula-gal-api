# Transparency flow — UX acceptance contract

These task scenarios are the Phase 0 frontend contract. Galician is the primary UI
language. Infrastructure/provider names stay out of user-facing copy.

## Upload and background processing

**Given** an authenticated institutional operator chooses a supported public recording,
**when** they complete the three-step upload wizard, **then**:

- validation happens before bytes are uploaded;
- the confirmation screen repeats title, public permission, media size, and automatic
  processing notice;
- real byte progress is announced while upload is measurable;
- leaving is discouraged only during the active byte upload;
- after upload, the editor opens immediately and explicitly says it is safe to leave;
- reopening on another browser shows the durable recording and current processing stage;
- a retryable failure has one clear retry action and keeps the last successful artifact.

Status copy:

| Machine state | Galician UI |
|---|---|
| `draft` | Borrador |
| `uploading` | Subindo a gravación… |
| `uploaded` | Gravación recibida |
| `transcribing` | Preparando a transcrición… Podes pechar esta páxina. |
| `review_required` | Hai cousas concretas por comprobar |
| `enriching` | Organizamos a sesión coas fontes… |
| `ready` | Lista para publicar |
| `published` | Publicada |
| `processing_failed` | Non se puido completar este paso. O traballo anterior está gardado. |
| `archived` | Arquivada |

## One required exception-only review

**Given** transcription completed, **when** the operator opens Review, **then**:

- the first view is a finite count grouped by concrete reason, never an accuracy score;
- selecting an issue seeks the recording, focuses its text, and exposes neighboring
  context;
- the smallest valid action—edit, assign, merge, confirm, or dismiss—is keyboard
  reachable;
- completing an action advances to the next unresolved issue;
- warnings do not block “Rematar a revisión da transcrición”;
- required issues do block it and the UI names the remaining count;
- unknown speakers may remain explicitly “Persoa non identificada”;
- the full transcript is available but is not a required linear pass.

Issue copy:

| Reason | Galician UI |
|---|---|
| unknown speaker | Quen fala neste treito? |
| speaker change | Comproba este cambio de voz |
| low log-probability span | O audio deste treito é difícil |
| probable proper name | Comproba este nome ou lugar |
| glossary mismatch | Pode haber un termo propio distinto |
| overlap/noise | Hai voces solapadas ou ruído |
| missing timing | Este treito non ten tempo fiable |
| invalid/empty segment | Este treito precisa unha corrección |

## Automatic agenda and guide

**Given** required transcript issues are resolved, **when** enrichment completes, **then**:

- normal agenda items say “Organizado automaticamente” and require no action;
- only ambiguous boundaries appear as a short “comprobacións rápidas” queue;
- the operator chooses a nearby transition or “Non se pode identificar”;
- topics and contributions appear automatically with “Ver evidencia”;
- consequential decisions are omitted until confirmed or document-supported;
- the panel summarizes generated items and exceptions; it is not a second exhaustive
  editor;
- missing agenda or failed enrichment never blocks a searchable transcript publication.

The normal added enrichment task must complete in 2–5 minutes. A sample over 10 minutes
fails the product gate and triggers simplification/automation work.

## Publication

**Given** the reviewed transcript and required metadata, **when** the operator prepares a
publication, **then**:

- the checklist visibly separates required facts from optional enrichment;
- preview uses the same public DTO as the anonymous page;
- the operator sees the assisted-guide label and the recording/source permission;
- publishing pins an immutable evidence revision;
- a later correction produces a new version and visible note;
- withdrawal immediately removes search access and new media links.

## Public search and evidence playback

**Given** an anonymous citizen, **when** they search with exact words, a question,
Galician, Spanish, a name, or a typo, **then**:

- the query remains in the URL;
- results are evidence cards, not chat messages;
- each card names the session/date, speaker or unidentified state, context, excerpt, and
  exact play time;
- match reasons use plain labels such as “frase exacta,” “nome,” or “significado
  relacionado”;
- selecting a result seeks without unexpected audible autoplay, highlights/focuses the
  evidence, and updates timestamp state;
- “Non atopamos evidencia” is distinct from a technical failure;
- filters can be cleared and do not erase the query;
- a shared result restores search context and timestamp.

## Accessibility acceptance

- All upload, issue, quick-check, publication, filter, result, and playback actions work
  with keyboard alone.
- Focus moves to the selected issue/evidence and returns predictably after dialogs.
- Upload progress, processing changes, result counts, errors, and completion are
  announced through appropriate live regions without excessive repetition.
- Labels do not rely on color or icon alone; contrast and visible focus meet WCAG 2.2 AA
  intent.
- The synchronized transcript exposes speaker and timestamp text to assistive
  technology.
- Video never autoplays with sound after search navigation.
- Motion respects `prefers-reduced-motion`.
- Mobile result cards retain excerpt, speaker, date, and the play-from-time action.
