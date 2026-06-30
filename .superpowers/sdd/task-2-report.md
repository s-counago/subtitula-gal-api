# Task 2 Report: `users` table + `User` entity + `UserRepository`

## What Was Implemented

Four files created verbatim from the task brief:

1. `src/main/resources/db/migration/V1__users.sql` — Flyway migration creating the `users` table with all required columns (uuid PK, email varchar(320) not null unique, email_verified boolean default false, password_hash varchar(100) nullable, display_name varchar(120) not null, timestamptz created_at/updated_at) plus `idx_users_email_lower` unique index on `lower(email)`.

2. `src/main/java/gal/subtitula/api/user/User.java` — JPA entity mapped to `users` table. Static factory `User.create(email, displayName, passwordHash)` lowercases email on creation. `@PreUpdate touch()` keeps `updatedAt` current. Only getters exposed (no setters for immutable fields). Matches the `{ id, email, displayName, emailVerified }` account JSON contract.

3. `src/main/java/gal/subtitula/api/user/UserRepository.java` — Spring Data JPA repository with derived queries `findByEmail` and `existsByEmail`.

4. `src/test/java/gal/subtitula/api/user/UserRepositoryTest.java` — Two tests: `savesAndFindsByEmail` (case-normalised lookup) and `rejectsDuplicateEmail` (DataIntegrityViolationException).

## TDD Evidence

### RED phase
No RED compile failure was captured separately because the brief specifies: "If you wrote the test before the entity, it fails to compile first — write entity/migration, then green." Per brief instructions, all files were created before the first test run. The entity/migration were prerequisites for the test to compile at all.

### GREEN phase
Command: `./mvnw test -Dtest=UserRepositoryTest`

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.406 s
[INFO] BUILD SUCCESS
```

Full suite: `./mvnw test`

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
Total time: 9.685 s
```

The `rejectsDuplicateEmail` test correctly triggers a Hibernate SQL error log (unique constraint violation) which is expected behavior — the exception is caught by `assertThrows`.

## Files Changed

- `src/main/resources/db/migration/V1__users.sql` (new)
- `src/main/java/gal/subtitula/api/user/User.java` (new)
- `src/main/java/gal/subtitula/api/user/UserRepository.java` (new)
- `src/test/java/gal/subtitula/api/user/UserRepositoryTest.java` (new)

## Commit

SHA: `66305b7` — `feat: users table, User entity, UserRepository`

## Self-Review

- Field names match brief exactly: `email`, `displayName`, `emailVerified`, `passwordHash`.
- Entity column names match SQL schema: `email_verified`, `password_hash`, `display_name`, `created_at`, `updated_at`. Hibernate `ddl-auto: validate` passed, confirming parity.
- `passwordHash` nullable for Google-only users — correct.
- Email lowercased at creation, matching the `lower(email)` index.
- No YAGNI violations: no service, controller, or extra methods added.
- Test output is pristine: 2/2 passing, 3/3 full suite passing.

## Concerns

None. Implementation is complete and clean.

---

## Fix: case-insensitive email lookup

### What was changed

**`src/main/java/gal/subtitula/api/user/UserRepository.java`** — Replaced the two Spring Data derived queries with explicit JPQL `@Query` annotations so lookups fold both sides through `lower()`:
- `findByEmail`: `select u from User u where lower(u.email) = lower(:email)`
- `existsByEmail`: `select case when count(u) > 0 then true else false end from User u where lower(u.email) = lower(:email)`

Added imports for `org.springframework.data.jpa.repository.Query` and `org.springframework.data.repository.query.Param`. Method names and signatures are unchanged.

**`src/test/java/gal/subtitula/api/user/UserRepositoryTest.java`** — Added `findsByEmailCaseInsensitive` test: saves a user with email `bob@example.com` (stored lowercased by `User.create`), then asserts `findByEmail("BOB@EXAMPLE.COM")` returns present and `existsByEmail("Bob@Example.Com")` returns true. (A distinct email was used because the base class has no rollback between tests and `ada@example.com` is already inserted by the sibling test.)

### Test command and result

```
./mvnw test -Dtest=UserRepositoryTest
```

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.646 s
[INFO] BUILD SUCCESS
Total time: 8.549 s
```

UserRepositoryTest 3/3 passing, pristine.
