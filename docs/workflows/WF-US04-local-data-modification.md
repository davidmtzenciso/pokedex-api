# WF-US04 — Local Data Modification

> **User story**: *Enable update operations for any Pokémon currently stored within the local database. Ensure robust validation: provide 404 responses for missing records, 400 status codes for malformed payloads, and incorporate further defensive logic as required.*
> **Depends on**: [WF-000](WF-000-foundation.md), [WF-US03](WF-US03-data-synchronization.md) (there must be something to modify), [WF-AUTH](WF-AUTH-user-management.md)
> **Delivers**: full CRUD over `/v1/pokedex/local`
> **Estimate**: M
> **Work units**: [WU-US04-A](../work-units/WU-US04-A-crud-use-cases.md) · [WU-US04-B](../work-units/WU-US04-B-crud-endpoints.md)

---

## 1. Summary

CRUD over the curated catalogue. The story names 404 and 400 explicitly and then asks for
"further defensive logic as required" — that phrase is the actual test, and this workflow
answers it with 409, 412, 502, and 504, each for a distinct and defensible reason.

---

## 2. Design decisions specific to this story

| # | Decision | Alternatives | Rationale | Consequence |
|---|---|---|---|---|
| 1 | Optimistic locking via `@Version`, 412 on conflict | Pessimistic lock; last-write-wins | Two curators editing different fields must not serialise, but a lost update is a data-loss bug nobody notices | Clients send `version` and handle 412 |
| 2 | `PATCH` restricted to proprietary fields | Allow patching replicated fields too | Replicated fields have an authority, and it is not the curator. Editing them would be overwritten on the next re-sync anyway | Attempting one is a 400 |
| 3 | Hard delete | Soft delete | Nothing needs recoverability or audit; a `deleted_at` predicate on every query is a permanent tax | Irreversible — a UI obligation to confirm — [ADR-0010](../adr/0010-hard-deletes.md) |
| 4 | Distinguish 409 from 412 | One conflict code | They mean different things: 409 is "that already exists", 412 is "someone changed it since you read it" | Two branches client-side |

---

## 3. Specification

| Verb | Path | Auth | Success | Errors |
|---|---|:---:|---|---|
| GET | `/v1/pokedex/local?page&size&region&tag&q` | 🔓 | 200 paged | 400 |
| GET | `/v1/pokedex/local/{id}` | 🔓 | 200 | 404 |
| POST | `/v1/pokedex/local` | 🔒 | 201 + `Location` | 400, 401, 409 |
| PUT | `/v1/pokedex/local/{id}` | 🔒 | 200 | 400, 401, 404, 412 |
| PATCH | `/v1/pokedex/local/{id}` | 🔒 | 200 | 400, 401, 404, 412 |
| DELETE | `/v1/pokedex/local/{id}` | 🔒 | 204 | 401, 404 |

`size` defaults to 10, maximum 100. Filters `region`, `tag`, and `q` (name substring)
compose.

Mutable via `PATCH`: `region`, `notes`, `tags`, curator `localizedNames`. Plus `version`,
which is required.

---

## 4. Domain delta

No new aggregates. Exercises invariants **I2** (name 1–60 chars), **I3** (mass and height
positive), **I4** (≤ 10 tags, case-insensitively distinct), **I7** (region from the closed
enum), **I9** (cascade delete).

Operations from [WF-000 §4.7](WF-000-foundation.md): `createLocal`, `updateLocal`,
`deleteLocal` — each with its PRE, POST, and error mapping.

---

## 9.5 Error paths

The story names the first two; the rest are the "further defensive logic".

| Condition | Response | Why it exists |
|---|---|---|
| Record not found | **404** `POKEMON_NOT_FOUND` | Named in the story |
| Malformed payload, failed validation | **400** `VALIDATION_ERROR` + `errors[]` per field | Named in the story |
| `pokeApiId` already present | 409 `DUPLICATE_POKEMON` | "Already exists" is not "not found" |
| `version` mismatch | 412 `STALE_VERSION` | Someone changed it since you read it — a distinct condition |
| `PATCH` targeting a replicated field | 400 with the field named | The curator is not that field's authority |
| Illegal state transition | 409 `ILLEGAL_STATE_TRANSITION` | |
| Unauthenticated mutation | 401, no row written | |
| Pagination out of range | 400 `INVALID_PAGINATION` | Reject, never clamp |

---

## 10. Acceptance criteria

**AC-US04-1**: Given a stored Pokémon, when `PATCH` sets `region` and `tags`, then 200
returns with the new values, `version` incremented, and `replicationState = CUSTOMIZED`.

**AC-US04-2 (the story's explicit 404)**: Given a non-existent id, when `PUT
/v1/pokedex/local/9999` is called, then **404** returns as `application/problem+json` with
`code = POKEMON_NOT_FOUND`.

**AC-US04-3 (the story's explicit 400)**: Given `mass: -5`, when `PUT` is called, then
**400** returns with `errors[]` naming the offending field.

**AC-US04-4**: Given two concurrent `PATCH` requests both sending `version = 3`, then
exactly one succeeds and the other receives **412** `STALE_VERSION`.

**AC-US04-5**: Given `DELETE`, then 204 returns, the row and its children are gone, and a
subsequent `GET` returns 404.

**AC-US04-6**: Given filters `region=KANTO&tag=starter`, then results narrow correctly and
pagination metadata reflects the filtered total.

**AC-US04-7**: The nine mandatory E2E scenarios pass — create 201, create duplicate 409, get
200, get 404, list 200, update 200, update 404, delete 204, get-after-delete 404.

---

## 12. Risks

| # | Risk | P | I | Score | Mitigation |
|---|---|:-:|:-:|:-:|---|
| R12 | A lost update goes unnoticed for months | Low | High | Y | `@Version` on the aggregate plus a concurrent-update component test |
| R13 | `PATCH` silently accepts a replicated field and the edit vanishes on re-sync | Med | Med | Y | The command object contains only proprietary fields; anything else is a 400 |
