# The Age of Machinery — Future Corrections & Implementation Guide

**Project:** The Age of Machinery  
**Target:** Minecraft Java Edition 1.21.1 · NeoForge 21.1.233 (Create+ compatibility) · Java 21  
**Package currently used:** `com.TheAgeOfMachinery`  
**Mod ID:** `theageofmachinery`  
**Document type:** Living engineering backlog / architecture decision record  
**Last consolidated:** 2026-10-25  

> **Purpose:** Preserve every deferred correction, limitation, and planned follow-up discussed while designing and implementing the quest system. This file deliberately separates **confirmed requirements**, **known deficiencies**, and **future features** so a suggested improvement is not mistaken for already-written code.
>
> **Scope:** The quest-mod development discussion and relevant earlier design decisions. The author has not inspected the local repository; code status below reflects implementations supplied and reviewed in chat, **not a fresh repository audit**. Confirm local implementations before checking items off.

## How to maintain this guide

- `[ ]` Not completed/verified; `[x]` completed and verified in the repository.
- **P0** = must fix before exposing progression/rewards to players. **P1** = essential for the first playable implementation. **P2** = polish or extensibility. **P3** = optional later work.
- Add a link to the commit or PR when closing an item, and note any design decision that changes the rule.
- **Do not treat UI concealment as secrecy:** future quest/reward data must also be absent from unauthorized client network payloads.

---

## 1. Confirmed product and gameplay rules (design baseline)

These are **decisions**, not open questions. Implementations must preserve them.

- [x] **Create-first and focused:** the mod is exclusively designed around the Create gameplay loop, rather than a generic multi-mod quest engine.
- [x] **Per-player progression:** each player has independent quest state, including in multiplayer; use UUID ownership and server-side authority.
- [x] **Exactly one active quest:** completed quests remain in history; locked/future quests do **not** require player-progress entries.
- [x] **Main and optional objectives:** main objectives are required; optional objectives offer extra rewards but cannot block advancement.
- [x] **Manual advancement:** satisfying all main objectives *enables* advancement; it does not automatically advance. `ReadyToAdvance` is derived from objective progress, not a separately persisted quest state.
- [x] **Optional-objective window:** a player may continue working on optional objectives after finishing the main objectives, until they manually advance. Advancing finalizes the quest and forfeits unfinished optional objectives.
- [x] **Strict spoiler secrecy:** do not reveal future quests or any reward for the current quest. Rewards become discoverable **only when the player advances/finalizes the quest**; show only rewards actually earned. The next quest is revealed only when unlocked.
- [x] **Per-entry reward claims:** after finalization, earned rewards are listed in a dedicated UI grouped by originating quest. Each entry can be claimed independently, **but not partially** (e.g., claim `100 XP`, `50 XP`, or `5 cogwheels` as three separate indivisible entries).
- [x] **Unclaimed rewards persist:** changing or finishing later quests must not delete unclaimed reward entries.
- [x] **Full delivery or no delivery:** if the inventory cannot accept all 5 cogwheels, reject that reward claim entirely and leave it unclaimed; never give 3/5 and consider it claimed. XP is awarded in full.
- [x] **Hints and ingredients:** spoiler-controlled progressive hints (three levels planned). On the *first* request to craft/build a particular item or machine, show a materials/ingredients checklist with quantities; these are guidance, **not reward previews**.
- [x] **Avoid arbitrary grind:** support exploration and multiple engineering solutions where practical; automate detection of gameplay actions rather than relying on manual completion wherever possible.

`[x]` in this section means **decision agreed**, not feature implemented.

---

## 2. Existing code covered in milestones (review baseline)

### Milestone 2.1 — core quest model

- [x] `QuestState`: `LOCKED`, `ACTIVE`, `COMPLETED`.
- [x] `QuestObjective`: immutable `record` with ID, description, target count, optional flag; validation.
- [x] `QuestDefinition`: immutable `record` with ID, title, description, ordered objectives; checks duplicate objective IDs and requires at least one main objective; `List.copyOf()`.
- [x] `QuestProgress`: mutable quest state and objective counts; guard against non-active changes; progress cap; computed `canAdvance()`; explicit `complete()`; `Map.copyOf()` snapshot.
- [x] Milestone 2.1 compiled successfully.

### Milestone 2.2 — player progress

- [x] `PlayerQuestProgress`: UUID, map of unlocked `QuestProgress` instances, nullable active quest ID.
- [x] Activation, active/unlocked lookup, snapshots, objective updates, completion, completed-history checks, and `hasActiveQuest()`.
- [x] Milestone 2.2 compiled successfully and was prepared for a commit.

### Milestone 2.3 — initial campaign manager

- [ ] **Verify working-tree build and commit.** Chat supplied the full `QuestManager.java` but a successful build **after adding this class** has not yet been reported.
- [x] Proposed `QuestManager`: ordered quest registration, definition lookup, per-player progress lookup/creation, start first quest, update active objective, check advancement, complete current quest, locate/activate next quest.

**Implementation accuracy check:** We discussed replacing the original overflow-safe `long` progress calculation with a safe `int`-only *remaining-capacity* calculation, rewriting a Stream predicate as a `for` loop, and strengthening `QuestDefinition` description validation. Ensure the local files actually contain the chosen revisions; the guide cannot confirm their saved state.

---

## 3. P0 — Correct before wiring up the Advance Quest button

### Q-001 — One authoritative quest advancement operation

**Status:** `[ ]` **P0** · Affected: `QuestManager`, `PlayerQuestProgress`, `QuestProgress`, future reward manager.

**Problem:** Current methods `completeActiveQuest()` and `activateNextQuest()` can be called separately. `completeActiveQuest()` finalizes the quest **without recording rewards**. Interruption between the actions could permanently lose earned rewards or leave progression inconsistent.

**Required correction:** Add one server-authoritative progression operation that validates the active quest and required objectives, determines earned main/optional rewards, records those reward entitlements, finalizes the quest, activates the correct next quest (or marks campaign completion), and persists the resulting player state. Design and test interruption/retry handling before enabling the real UI button.

**Acceptance:** An advancement request never yields a completed quest missing its earned reward entitlements; retrying does not duplicate those entitlements; future quest activation cannot bypass eligibility.

### Q-002 — Determine next quest from authoritative player state

**Status:** `[ ]` **P0** · Affected: `QuestManager.activateNextQuest(UUID, String)`, `getNextQuestDefinition()`.

**Problem:** The public method trusts a caller-supplied `completedQuestId`. We explicitly flagged it as insufficient to prevent progression mistakes or campaign skipping.

**Required correction:** Derive the predecessor/current progression position on the server using the player's recorded history and registered campaign order; validate the exact eligible successor. Do not allow arbitrary completed IDs from client requests to select a future quest. Add tests for stale, reordered, replayed, and skipped IDs.

**Acceptance:** Only the actual next quest can be activated, at most once, regardless of supplied or replayed inputs.

### Q-003 — No reward information before quest finalization

**Status:** `[ ]` **P0** · Affected: quest/reward API, network payloads, journal UI.

**Problem:** Reward definitions must remain undisclosed even while the current quest is active and even if all main objectives are complete. Hiding widgets alone is insufficient if the client receives all reward data.

**Required correction:** Keep full quest/reward definitions server-side. Create restricted client-facing DTO/payloads containing the active quest's permitted title/description/objectives/hints/ingredient lists; previously completed quest history; and **only already-unlocked** reward entitlements. Never send future quest definitions, quest order, undisclosed rewards, or hidden objective data intended to remain secret.

**Acceptance:** Inspecting client messages/state before advancement reveals neither future quests nor rewards of the active quest; earned rewards appear after successful finalization.

### Q-004 — Restrict access to mutable player and quest state

**Status:** `[ ]` **P0** · Affected: `QuestManager.getOrCreatePlayerProgress()`, `PlayerQuestProgress.getQuestProgress()`, `getActiveQuestProgress()`, `getQuestProgressSnapshot()`, `QuestProgress.activate()/addProgress()/complete()`.

**Problem:** `Map.copyOf(quests)` makes the map unmodifiable, **not its `QuestProgress` values**. `Optional<QuestProgress>` still exposes mutable quest objects. Current public methods let other code bypass `QuestManager` checks.

**Required correction:** Separate read-only snapshots/views from server-side mutation APIs. Limit mutable `QuestProgress` access, ideally through package-private/internal methods or a dedicated controlled service. Avoid exposing mutable state across network boundaries. `QuestProgress.getObjectiveProgressSnapshot()` is safe for its immutable integer values, but verify all future value types.

**Acceptance:** Client-facing and general caller APIs cannot directly activate, advance, or mutate quest progress by holding a returned reference.

### Q-005 — Server validates objective progress and reward claims

**Status:** `[ ]` **P0** · Affected: gameplay events, `QuestManager.addObjectiveProgress()`, future reward/network handlers.

**Problem:** `addObjectiveProgress()` accepts an objective ID and arbitrary count. The current method is intended only for trusted server code, and must never become a client-accessible increment endpoint.

**Required correction:** Recognize validated Minecraft/Create server events, match them to active objectives, and calculate accepted increments server-side. Validate player UUID from the server connection rather than client-supplied identity. Reward claim requests should contain only entitlement IDs; validate owner, unlocked/unclaimed status, and delivery capacity server-side.

**Acceptance:** Crafted packets, repeated UI actions, and arbitrary amount/quest IDs cannot award progress, unlock content, or duplicate rewards.

### Q-006 — Idempotent reward claims, atomic full-stack item delivery

**Status:** `[ ]` **P0** · Affected: reward entitlement model, claim workflow, inventory/XP delivery, persistence.

**Problem:** The reward UI offers independently claimable entries but **never** partial claims. Inventory overflow and duplicated click/network events must not lead to partial or repeated grants.

**Required correction:** Assign stable entitlement IDs unique to player + quest + reward entry. Validate before delivery, ensure inventory can accept the entire item count (including legal stack splitting across slots if needed), and update claim state exactly once in a controlled server operation. Handle XP as an indivisible entry. Define and test interruption/crash consistency for inventory/XP versus saved claim state; Minecraft inventory and mod saved data may have different persistence timing, so do **not** assume a database-style transaction exists.

**Acceptance:** Claiming `5 cogwheels` grants exactly five or zero; claiming `100 XP` grants exactly 100 or zero; replay never awards more than once.

### Q-007 — Per-player persistent save/load and consistency

**Status:** `[ ]` **P0** · Affected: NeoForge saved data, player login/logout, world shutdown, reward inventory.

**Problem:** Current `QuestManager` stores players only in an in-memory `Map<UUID, PlayerQuestProgress>`. No server persistence exists yet.

**Required correction:** Implement world/server-owned persistent player progression and reward entitlements, keyed by UUID; handle state restoration, dirty marking, restarts, and player rejoining. Validate loaded values: active ID must exist and map to exactly one `ACTIVE` quest; no completed quest can become active; progress values must be within each objective's allowed range; claimed rewards remain claimed. Plan schema/version migration for future JSON or save format updates. Ensure world-specific isolation and lifecycle cleanup.

**Acceptance:** Restarting the world/server preserves player objectives, current quest, completed quests, earned/unclaimed/claimed rewards, without granting duplicate progress or corrupting states.

---

## 4. P1 — Reward system and UI

### R-001 — Reward definitions and entitlement inventory

**Status:** `[ ]` **P1** · Proposed files: `reward/RewardDefinition.java`, `RewardType.java`, `RewardManager.java`, `PlayerRewardInventory.java`, `RewardClaimState.java` (names are proposals).

**Required:** Modular rewards for Minecraft XP, items (including Create components), and future unlock/cosmetic types. Separate immutable **reward definitions** from mutable, per-player **earned entitlements** and delivery state. Preserve unique per-entry identities even when two entries award XP. Attach entries to the originating quest and distinguish main versus optional reward sources.

**Acceptance:** Example: a quest awarding `100 XP`, `5 cogwheels`, and `50 XP` optional unlocks **three** independently claimable entries only if that optional objective was completed before advancement.

### R-002 — Earned optional rewards are frozen at advancement

**Status:** `[ ]` **P1**.

**Required:** Determine completed optional objectives when the player clicks Advance, create entitlements only for those, forfeit incomplete optional rewards, and reject post-completion progress changes. Do not accidentally award optional rewards for a partially complete objective unless the definition explicitly says so (not currently planned).

### R-003 — Rewards UI and quest grouping

**Status:** `[ ]` **P1** · Proposed: `client/QuestJournalScreen.java` plus a rewards screen or panel.

**Required:** Group earned reward entries by originating quest. Show unclaimed and claimed status, source (main/optional), type, quantity, and a per-entry **Claim** action. Permit claims at any later time, including after many other quests. Show inventory-full rejection without altering entitlement state. The UI prototype discussed is illustrative only, not implemented in Minecraft.

### R-004 — Quest journal, progress, and controlled reveal

**Status:** `[ ]` **P1**.

**Required:** Active quest title/description/main and optional objectives, progress and Ready-to-Advance state, manual Advance button, previously completed history, progressive hints, first-introduction ingredients checklist, and post-finalization reward reveal. **No** future quest preview, future rewards, or current rewards.

### R-005 — Network protocol and client synchronization

**Status:** `[ ]` **P1**.

**Required:** Server → client restricted quest view and unlocked entitlement updates; client → server intent messages (advance, claim, request permissible hints), not arbitrary state changes. Synchronize after login, world load, objective progress changes, successful advancement, and reward claims. Define handling of stale/repeated messages and reconnection.

---

## 5. P1 — Quest definitions and content

### D-001 — JSON-driven quest definitions

**Status:** `[ ]` **P1**.

**Required:** Move hardcoded registrations to JSON quest definitions, immutable after parsing/validation. Define a schema with stable namespaced IDs such as `theageofmachinery:first_settlement`, display metadata, main/optional objectives, target counts, hints, ingredient requirements, reward entries, and campaign sequencing. Validate duplicate IDs, missing prerequisites, invalid items/targets, and cycles if branching is introduced. Decide whether data pack reload is supported; handle already-saved progress when definitions change.

### D-002 — Stable IDs and Minecraft registry types

**Status:** `[ ]` **P1**.

**Problem:** Current APIs use `String` IDs. This is sufficient for the Java model but error-prone when integrating Minecraft registries.

**Required:** Evaluate `ResourceLocation` for quest IDs, objective targets, item IDs, and reward identities where appropriate. Establish explicit serialization rules and a stable ID policy so changing user-facing titles doesn't invalidate saves. Avoid prematurely converting unrelated display strings into registry IDs.

### D-003 — Objective types and event matching

**Status:** `[ ]` **P1**.

**Problem:** `QuestObjective` currently contains only ID, description, count, and optional flag. The game cannot infer from these whether to count crafting, placing, processing, or transport.

**Required:** Introduce objective type + target specification and dispatch verified server events to matching active objectives. Initial candidates: item crafting, block placement, recipe processing, item transportation, and automation milestones. Avoid brittle item-only heuristics where multiple Create machines can produce the same output.

### D-004 — Campaign ordering and registration guarantees

**Status:** `[ ]` **P1**.

**Problem:** `registerQuest()` implicitly makes registration order equal campaign order. With JSON reloads or unordered discovery, this may become nondeterministic.

**Required:** Use an explicit campaign order/prerequisite structure, validate missing/duplicate entries, and define deterministic first-quest selection and end-of-campaign handling. Keep definitions and ordering private to the server.

### D-005 — First-use ingredient checklist

**Status:** `[ ]` **P1**.

**Required:** When the active quest first asks the player to create an item/machine, expose required ingredients and quantities. Decide whether checklists are authored in quest JSON, derived from recipes, or a hybrid for multi-step Create recipes. Only reveal materials that are allowed at the current quest stage. Do not confuse recipe ingredients with hidden quest rewards.

### D-006 — Progressive hints without spoilers

**Status:** `[ ]` **P1**.

**Required:** Define up to three increasingly specific hint levels, unlocked on request (not by leaking full campaign data). Preserve the discovery-based campaign; no future-tech spoilers in journal, hints, or network payloads. Support the Create+ 1.21.1 environment, with recipe/Ponder guidance for currently permitted technology.

---

## 6. P1/P2 — Domain-model correctness and API hardening

### C-001 — Review `QuestProgress.addProgress()` cap calculation

**Status:** `[ ]` **P1** · Affected: `QuestProgress.addProgress()`.

**Discussion:** We initially used `(int) Math.min((long) required, (long) current + amount)` to prevent int overflow. User preferred simpler `int` math, so we chose a safe *remaining-capacity* calculation:

```java
int current = getObjectiveProgress(objective.id());
int remaining = objective.requiredAmount() - current;
int updated = current + Math.min(amount, remaining);
```

**Required:** Verify that this is the actual local code. It is overflow-safe **only if** saved/current progress is kept within `0..requiredAmount` and `amount > 0`. Enforce these invariants when deserializing save data. Do not replace this with naïve `Math.min(required, current + amount)`; addition can overflow *before* clamping.

### C-002 — Ensure `QuestDefinition` description validation matches decision

**Status:** `[ ]` **P2** · Affected: `QuestDefinition` compact constructor.

**Discussion:** We changed the intended validation from `Objects.requireNonNull(description, ...)` to `if (description == null || description.isBlank()) ...` so every quest has a nonblank description. Verify this local code change; `Objects.requireNonNull()` alone accepts `""` and whitespace.

### C-003 — Trust boundary around `QuestProgress` methods

**Status:** `[ ]` **P1**.

**Problem:** `QuestProgress.addProgress(QuestObjective, amount)` does not itself prove that the objective belongs to that quest; `PlayerQuestProgress.addObjectiveProgress()` currently checks membership. Other callers could bypass this validation through public methods.

**Required:** Restrict call sites/accessibility and make objective identity validation part of a trusted, definition-aware mutation path. Consider defensive consistency checks on `canAdvance()` and `complete()` for saved/corrupted states.

### C-004 — Return types and missing-value conventions

**Status:** `[ ]` **P2**.

**Current decision:** Use `Optional<QuestProgress>` for lookup results that may be absent. Favor explicit `isPresent()` / guarded `.get()` when learning; do not force `.ifPresent(lambda)` or Stream chains where a loop is clearer. Use `null` internally for absent `activeQuestId` only while invariants are controlled. Consider read-only DTOs for client-facing lookups.

### C-005 — Define failure/error reporting beyond `boolean`

**Status:** `[ ]` **P2**.

**Problem:** `boolean` is appropriate for the initial model but doesn't tell UI or logs *why* activation, advancement, or claiming failed.

**Required:** When integrating UI/networking, consider well-defined failure/result types or enums (`ALREADY_ACTIVE`, `OBJECTIVE_INCOMPLETE`, `INVENTORY_FULL`, `ALREADY_CLAIMED`, etc.) without introducing excessive abstraction prematurely.

### C-006 — Decide API naming and package conventions before wider release

**Status:** `[ ]` **P3**.

**Discussion:** Project currently uses Java package `com.TheAgeOfMachinery` and Gradle group `com.TheAgeOfMachinery`. Java naming conventions normally use all-lowercase package segments, but this casing was explicitly chosen and builds successfully.

**Required:** Decide whether to retain the existing public package or migrate to a lowercase identifier before substantial published APIs/serialized references depend on it. **Do not silently rename the project or package**; this is a convention question, not a runtime bug.

---

## 7. P1/P2 — NeoForge/Create integration, lifecycle, and testing

### I-001 — NeoForge 1.21.1 and Create+ version compatibility

**Status:** `[ ]` **P1**.

**Known setup:** Java 21; NeoForge **21.1.233** matched the user's Create+ instance after an earlier mismatch with **21.1.265**; successful Modrinth load/build was reported for the base mod. Keep development configuration aligned with the targeted profile. A `runClient` development instance does not automatically contain Create+ mods or saves.

**Required:** Declare/verify Create dependency and its compatible APIs when integration begins, and test in both a controlled dev environment and a backed-up Modrinth/Create+ instance. Don't treat base-mod loading as proof of Create event compatibility.

### I-002 — Detect real Create engineering achievements

**Status:** `[ ]` **P1/P2**.

**Problem:** Crafting, machine processing, automation, and transportation are not all reliably represented by basic vanilla craft events.

**Required:** Research suitable NeoForge/Create hooks; handle placed blocks, processing results, inventories, conveyors, deployed mechanisms, and genuinely operational constructions as objectives require. Design alternatives where vanilla/Forge-style events cannot prove a working machine. Avoid awarding progress for simply holding preexisting items unless the objective explicitly allows it. Avoid progress double-counting on repeated events.

### I-003 — Multiplayer/session isolation

**Status:** `[ ]` **P1**.

**Required:** Key saves by actual authenticated UUIDs, maintain independent progression and entitlements, and route only the appropriate player's view to their client. Consider world/server lifecycle and disconnected players, new logins, and resync. Test two players completing the same quest at different times.

### I-004 — Automated tests

**Status:** `[ ]` **P1**.

**Required test categories:**

- Definition rejects null/blank IDs/descriptions, duplicate objective IDs, empty/all-optional objective sets, invalid counts.
- Progress caps correctly, including near `Integer.MAX_VALUE`; ignores nonpositive increments and completed quests.
- Optional objectives never block advancement but can be completed after the main objectives and before advancement.
- Exactly one active quest; completed quests cannot be repeated or reactivated.
- Wrong quest/objective IDs and stale previous quest IDs are rejected; no skipping.
- Two UUIDs have isolated progress and rewards.
- Reward entitlements appear only after advancement; unearned optional entries never appear.
- Claim full entry or none; cannot claim a fraction; inventory full leaves entitlement unclaimed.
- Duplicate requests/restarts cannot duplicate XP or items; saved state restores correctly.
- Client synchronization does not expose future quest definitions or unrevealed reward metadata.

**Tooling:** Start with Java unit tests of domain logic, then add game/integration tests where Minecraft/NeoForge behavior is involved.

### I-005 — Logging, diagnostics, and schema upgrades

**Status:** `[ ]` **P2**.

**Required:** Add clear diagnostics for invalid definitions, missing quest IDs, failed advancement, rejected claims, persistence failures, and incompatible save/schema versions. Define a strategy for removed/renamed quest IDs without silently corrupting player history.

### I-006 — Documentation for a public GitHub portfolio

**Status:** `[ ]` **P2**.

**Required:** Maintain a clear README, setup/build instructions for Java 21 / Minecraft 1.21.1 / NeoForge, optional test profile notes, architecture diagram, quest/reward JSON format, development roadmap, and human-readable changelog. Clearly distinguish working features from proposed features in public documentation.

---

## 8. Prioritized implementation sequence

1. **Now:** Verify `QuestManager.java` compiles and verify the three locally chosen revisions (`QuestDefinition` description, imperative `canAdvance()`, overflow-safe `int` cap). Commit Milestone 2.3.
2. **Next:** Create immutable reward definitions and per-player reward entitlement data; test visibility/claim semantics without a UI.
3. **Before gameplay advancement:** Implement Q-001 through Q-007: authoritative advancement, correct successor, secrecy, mutation boundaries, claim idempotency, and persistence integrity.
4. **Then:** JSON quest schema + explicit order, objective types, event matching, restricted network views.
5. **Then:** Journal/reward UI, server-driven updates, first-use ingredient lists, hints, Create integration.
6. **Before public release:** Automated regression tests, multiplayer/restart/crash/replay tests, compatibility verification, documentation.

> **Implementation dependency:** This sequence is a planning outline, not permission to wire the current `completeActiveQuest()` directly to a real player action. Rewards and persistence must be coordinated before real advancement is exposed.

---

## 9. Open design decisions (not yet finalized)

These are **questions to resolve during implementation**, not prior commitments:

- **Advancement durability:** exact strategy for handling partial writes and crash recovery across player quest state, reward entitlements, and Minecraft inventory/XP.
- **Reward claim delivery:** how to preflight inventory insertion and protect against races or unexpected slot changes; where XP grant and claimed-state persistence are ordered.
- **Quest definition updates:** how to migrate old saved objective IDs/progress after JSON data pack updates, and whether definitions can reload live.
- **Create objective verification:** how to prove a machine is functional rather than merely built/placed, without forbidding creative alternate solutions.
- **Rewards/UI presentation:** whether newly unlocked rewards should produce a notification in addition to appearing in the grouped Rewards UI.
- **Progress data exposure:** exact read-only API/DTO shape versus package-private domain methods; choose the simplest safe design.
- **Failure feedback:** whether to use result enums or richer error objects once the UI needs actionable messages.

---

## 10. Revision log

| Date | Change |
|---|---|
| 2026-10-25 | Initial consolidation of decisions, deferred corrections, limitations, and planned features from the quest-mod discussion. |

**Maintenance rule:** Whenever we say "we'll fix that later", add or update an item here **in the same development milestone**. Never close an item solely because the build succeeds; verify its acceptance criteria.
