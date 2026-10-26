# The Age of Machinery — Future Corrections & Implementation Guide

**Project:** The Age of Machinery  
**Target:** Minecraft 1.21.1 · NeoForge 21.1.233 · Java 21 · Create/Create+ integration planned  
**Mod ID:** `theageofmachinery` · **Java package:** `com.TheAgeOfMachinery`  
**Document:** Living engineering backlog and architecture decisions  
**Last consolidated:** **26 October 2026 — Milestone 2.11.6 (build, manual tests, commit reported successful)**

> **Status precision:** This guide summarizes developer-reported builds, local in-game testing and commits, not an independent repository/CI audit. **Two-player multiplayer verification was not possible and remains pending.** Manual restart tests confirm normal persistence but **not crash-atomic reward delivery**. No real gameplay objective listeners, Create integration, data-driven JSON content, networking or UI have been completed.

## How to maintain this guide

- `[x]` = implemented and reported verified for the stated scope; `[~]` = partially implemented, validated only for some cases, or follow-up required; `[ ]` = open/not verified.
- **P0:** correctness or safety critical for exposed gameplay/reward claims; **P1:** first playable release; **P2:** improvements; **P3:** optional decision.
- Keep existing issue IDs stable across updates. A closed issue does not close other explicitly linked risks (e.g., verified normal delivery does not close crash recovery).
- Record test limitations rather than labeling untested scenarios as passed; update statuses after code *and* acceptance tests, not builds alone.

---

## 1. Confirmed gameplay rules (design baseline)

These are agreed product rules; `[x]` below means **decision confirmed**, not necessarily a finished UI feature.

- [x] Create-focused, discovery-first campaign; avoid arbitrary grind and reward engineering solutions.
- [x] Per-player UUID ownership and server-side authority; intended for multiplayer.
- [x] One active quest, with completed history and no need to populate future quest progress records.
- [x] Mandatory objectives gate **manual** advancement; optional objectives can continue until the player advances.
- [x] Optional progress is frozen on advancement; unfinished optional bonuses are forfeited.
- [x] Strict spoiler behavior: no future quests and **no current quest rewards** revealed before finalization; only earned rewards afterward.
- [x] Rewards grouped by originating quest, independently claimable in full, retained across later quests and world restarts.
- [x] Item delivery is all-or-nothing; do not consume a reward if the entire quantity cannot fit.
- [x] Three levels of spoiler-controlled hints planned; first-time crafting/building objectives require a materials checklist with quantities.
- [x] Objective progress should ultimately come from validated gameplay events, not client-supplied quantities.

---

## 2. Milestone and verification ledger

| Milestone | Implementation | Developer-reported verification |
|---|---|---|
| **2.1–2.4** | Quest state, objectives, definitions, per-player progress, reward types/inventories/managers | Built; domain behavior exercised later |
| **2.5–2.6** | Real Minecraft XP/item delivery; separate earned entries; all-or-nothing inventory insertion; debug claiming | In-game XP/items, full-inventory failure and retry, duplicate rejection passed |
| **2.7.1–2.7.7** | Minecraft `SavedData` and NBT codecs; save format v3; quest and reward storage | Built; quest persistence verified across restarts |
| **2.7.8** | Reward persistence and claim-state restart tests | **Passed**: claimed/unclaimed/restored; no repeat grant on normal restart |
| **2.8.1–2.8.3** | Persistent `QuestManager`/`RewardManager` and integrated manager tests | **Passed** including two-quest persistence/reward test |
| **2.9.1–2.9.3** | `QuestRewardDefinition`, coordinated completion, optional bonuses and next quest | **Passed**: basic/bonus scenarios, campaign completion, normal restart |
| **2.10.1–2.10.3** | Unified `PlayerState`, staged transaction, deep-copy snapshots, revision-checked commit | **Passed** including detached-state and stale-commit tests |
| **2.10.4A–2.10.4B** | Revision-checked quest/reward writes and synchronous claim guard | **Passed** including failed delivery, reentrancy, normal restart |
| **2.10.5–2.10.6B** | Removed legacy lifecycle bypasses/unconditional store methods, failure injection, isolated debug migration | **Passed**: conflicting entitlements, stale commit, pre-delivery exception, recovery of test harness |
| **2.11.1–2.11.2** | Per-server `CampaignRuntime`; built-in two-quest definitions and registration | **Passed** across server restarts; stable campaign ordering |
| **2.11.3–2.11.5** | Runtime operations with real player UUID, automatic login initialization, `ServerPlayer` authority, server thread checks | **Passed**: prototype progression, claims, reconnect, invalid operations |
| **2.11.6** | `/tamruntime verify` negative-path checks and `/tamruntime audit` | **Single-player tests passed and committed. Two-player test NOT performed.** |

**Current state:** Runtime owns one advancement service (with its single registered `QuestManager`) and one reward manager, both backed by the same `CampaignSavedData`. Save schema remains **`FormatVersion = 3`** with `TestValue`, `Players`, and `RewardInventories`. All real prototype progression currently comes from development commands, not Minecraft events.

**Explicit test gap:** Multiplayer architecture uses UUID isolation and a read-only audit; the user **cannot currently test two players**, so no claim is made about verified simultaneous independent progression. No automated test suite or crash-atomicity result is reported.

---
## 3. Quest advancement, authority and persistence

### Q-001 — One authoritative quest advancement operation

**Status:** `[~]` **P0**

**Current evidence:** Staged `CampaignAdvancementService.advanceQuest()` calculates earned rewards, prepares detached quest/reward objects, and commits both together with one revision-checked player-state replacement. In-game scenarios and failure-injection tests passed; `QuestManager` direct complete/next methods were removed.

**Remaining / acceptance:** Normal in-memory operation is coordinated; a process crash during saving and delivery remains outside these guarantees (P-005). Only the runtime should expose advancement to real players.

### Q-002 — Determine next quest from authoritative player state

**Status:** `[x]` **P0**

**Current evidence:** Advancement derives the current active quest and registered successor from server-owned state; caller-supplied completed quest IDs are no longer used for production advancement. Legacy `QuestManager.activateNextQuest()` was removed in 2.10.5.

**Remaining / acceptance:** Maintain the server-side ordering check when JSON definitions and client requests are introduced; test reordered/invalid content migrations separately.

### Q-003 — No reward information before quest finalization

**Status:** `[~]` **P0**

**Current evidence:** All current prototype campaign definitions and reward calculations remain server-side. `CampaignRuntime.getUnlockedRewards()` returns only earned rewards; there is no player-facing UI or gameplay network payload yet. Operator-only `/tamruntime campaign` intentionally displays all prototype IDs for diagnostics.

**Remaining / acceptance:** Create restricted server-to-client DTOs showing only authorized active quest/history and already-unlocked rewards; never send the current quest reward list or future definitions. Local client JAR content cannot be considered secret (P-007).

### Q-004 — Restrict access to mutable player and quest state

**Status:** `[~]` **P0**

**Current evidence:** `CampaignSavedData` now returns detached deep copies and snapshots, uses copy-on-write, and rejects stale revision commits; unconditional `storePlayer...` APIs were removed. Manager lifecycle bypass methods were removed in 2.10.5.

**Remaining / acceptance:** Domain objects remain mutable and trusted storage methods remain public in Java; avoid routing UI/network code to raw state. Finish a controlled public API boundary and protect save initialization/transaction methods against misuse.

### Q-005 — Server validates objective progress and reward claims

**Status:** `[~]` **P0**

**Current evidence:** Runtime operations now require `ServerPlayer` identity, verify server ownership/online player for gameplay mutations, and enforce server-thread execution. Invalid objective IDs, zero/negative amounts and nonexistent reward claims were rejected without revision change in 2.11.6.

**Remaining / acceptance:** Only trusted server-side crafting, collection, and Create events may call `addObjectiveProgress`; the later client protocol may submit advance/claim intent but never arbitrary progress counts or UUIDs. Test malicious/replayed packets when networking exists.

### Q-006 — Idempotent reward claims, atomic full-stack item delivery

**Status:** `[~]` **P0**

**Current evidence:** Whole-reward inventory preflight and retry were verified in-game. `executeRewardClaim()` rejects duplicate/reentrant claims, blocks conflicting writes, and updates the claim state without a post-delivery revision check. Failed and successful claim states survive restarts.

**Remaining / acceptance:** No durable atomicity between Minecraft XP/items and `SavedData`; callback exceptions after side effects are uncertain. Define recovery/quarantine policy and test oversized stacks, exotic inventory states, and repeated network requests (P-005).

### Q-007 — Per-player persistent save/load and consistency

**Status:** `[~]` **P0**

**Current evidence:** `CampaignSavedData` v3 persists UUID-keyed quest/reward state. Normal restarts, returning-player login, staged advancement, earned claims, and server runtime recreation were all manually verified through 2.11.6.

**Remaining / acceptance:** Two-player verification is pending (I-003). Schema migration, corrupt-data recovery, and definition-version compatibility need explicit tests (P-003).

---

## 4. Rewards, delivery and user-facing features

### R-001 — Reward definitions and entitlement inventory

**Status:** `[x]` **P1**

**Current evidence:** XP/item reward records, entitlements grouped by quest, immutable claim entries, optional reward calculations, SavedData persistence, and real Minecraft delivery are implemented. In-game tests cover unlocking, claiming, duplicate rejection, inventory-full refusal, and restart restoration.

**Remaining / acceptance:** Future reward types and client presentation are separate enhancements; crash recovery is tracked by P-005 and optional-source metadata by R-007.

### R-002 — Earned optional rewards are frozen at advancement

**Status:** `[x]` **P1**

**Current evidence:** `QuestRewardDefinition.calculateEarnedRewards()` selects optional rewards only for completed objectives while a quest is active. Staged advancement freezes exactly the earned entries. Basic/bonus scenarios were tested in 2.9.3 and 2.10.2.

**Remaining / acceptance:** Preserve this rule when switching to JSON and when integrating actual gameplay events. No optional progress is accepted once its quest is finalized.

### R-003 — Rewards UI and quest grouping

**Status:** `[ ]` **P1**

**Current evidence:** The persistent reward groups and individual claim states already exist in server data, but no client rewards screen is implemented.

**Remaining / acceptance:** Implement a grouped earned-rewards view with separate Claim actions, quantities, claimed/unclaimed indicators, helpful inventory-full feedback, and no current/future-quest reward previews.

### R-004 — Quest journal, progress, and controlled reveal

**Status:** `[ ]` **P1**

**Current evidence:** The runtime exposes server-side active-quest information, but no Quest Journal UI exists.

**Remaining / acceptance:** Build a journal with active title/description/main and optional progress, ready-to-advance state, manual Advance action, completed history, three-level hint controls, and first-use ingredient lists. Apply network-level spoiler filtering.

### R-005 — Network protocol and client synchronization

**Status:** `[ ]` **P1**

**Current evidence:** No client↔server campaign payloads or synchronization system is implemented. Debug commands remain server/permission-gated.

**Remaining / acceptance:** Define server-to-client minimal authorized views and client-to-server advance/claim/hint intent packets. Derive player from packet sender; validate lifecycle and sync on join, objective changes, advancement, claims and reconnect.

### R-006 — Separate reward claiming from Minecraft delivery, and enforce complete delivery

**Status:** `[~]` **P0**

**Current evidence:** `MinecraftRewardDelivery` preflights inventory capacity and uses whole-item insertion; tests verified 5 iron ingots, full-inventory rejection and retry. Reward claims execute through a guarded server-side operation.

**Remaining / acceptance:** Test amounts exceeding a stack, nonstandard stack limits, and non-ordinary inventories. Consider actionable delivery results beyond `boolean`. Exception-after-side-effect and crash recovery remain unresolved.

### R-007 — Stable reward identity, metadata, and optional-source accounting

**Status:** `[~]` **P1**

**Current evidence:** Main and optional rewards are calculated from validated `QuestRewardDefinition` maps and unlocked under `(player UUID, quest ID, reward ID)`. Duplicate IDs within a quest are rejected. `RewardEntry` does not explicitly encode main/optional source metadata.

**Remaining / acceptance:** Define stable source metadata for UI/JSON, preserve historical reward amounts across definition updates, and decide semantics of zero-reward groups. Do not reconstruct earned rewards from new definitions on reload.

### R-008 — Immutable claim-state replacement and storage consistency

**Status:** `[x]` **P1**

**Current evidence:** Immutable `RewardEntry` replacement, `LinkedHashMap` reward grouping, detached storage snapshots, controlled claim updates, and v3 save/load were manually verified across world restarts.

**Remaining / acceptance:** A dedicated automated ordering test is still worthwhile; complex recovery and migration remain tracked separately.

---

## 5. Quest definitions and content

### D-001 — JSON-driven quest definitions

**Status:** `[ ]` **P1**

**Current evidence:** Prototype quests are currently defined in Java using `BuiltInCampaign.create()`; there is no JSON loader.

**Remaining / acceptance:** Create a validated data-driven schema for stable IDs, objective types/targets, optional/main rewards, hints, ingredient lists and explicit order. Decide reload behavior, migration, and compatibility with existing saves.

### D-002 — Stable IDs and Minecraft registry types

**Status:** `[ ]` **P1**

**Current evidence:** Quest/objective/reward IDs currently use strings, while Minecraft item delivery parses resource locations through item registries.

**Remaining / acceptance:** Introduce resource-aware, validated target specifications and stable save keys; do not let renaming display text change persisted IDs. Validate Create registry entries once Create dependency is configured.

### D-003 — Objective types and event matching

**Status:** `[ ]` **P1**

**Current evidence:** Current `QuestObjective` contains ID, description, required count and optional flag; it does not express which Minecraft action or registry target it should match.

**Remaining / acceptance:** Add objective type/target metadata and trusted event matching (craft, acquire, place, process, automate). Count verified server events only; keep old objective IDs stable across saves.

### D-004 — Campaign ordering and registration guarantees

**Status:** `[~]` **P1**

**Current evidence:** `CampaignDefinition` validates nonempty ordered lists and duplicate quest IDs. A server-scoped runtime registers a deterministic two-quest built-in campaign on startup; basic/final advancement passed.

**Remaining / acceptance:** When JSON replaces hardcoded definitions, validate deterministic ordering, missing references, content changes and possible branching/prerequisites. Never expose full order to ordinary clients.

### D-005 — First-use ingredient checklist

**Status:** `[~]` **P1**

**Current evidence:** Prototype descriptions list 4 planks for a crafting table and 8 cobblestone for a furnace, reflecting the first-use materials rule. This is authored text, not an ingredient-list subsystem.

**Remaining / acceptance:** Define structured ingredient quantities, Create multi-stage recipes, and permitted client disclosure. Render a checklist when first asking to craft/build a given item or machine.

### D-006 — Progressive hints without spoilers

**Status:** `[ ]` **P1**

**Current evidence:** Three progressive hint levels are a confirmed product requirement; no hint system is implemented.

**Remaining / acceptance:** Add per-objective permitted hint tiers and server-controlled disclosure. Avoid future quest or reward leaks and use recipe/Ponder guidance only when permitted.

---

## 6. Domain model and API correctness

### C-001 — Review `QuestProgress.addProgress()` cap calculation

**Status:** `[~]` **P1**

**Current evidence:** The `int`-only remaining-capacity calculation was chosen and repeated progression tests passed. The current local source was not independently inspected for this document.

**Remaining / acceptance:** Audit the actual `QuestProgress` implementation and deserialized bounds. Avoid `current + amount` overflowing before clamping; reject impossible saved counts.

### C-002 — Ensure `QuestDefinition` description validation matches decision

**Status:** `[~]` **P2**

**Current evidence:** Nonblank `QuestDefinition` descriptions were part of the code supplied and built successfully; no independent repository inspection is recorded.

**Remaining / acceptance:** Keep null/blank validation and verify with an automated constructor test.

### C-003 — Trust boundary around `QuestProgress` methods

**Status:** `[~]` **P1**

**Current evidence:** Normal runtime objective updates validate the active registered quest, ID, count and revision; storage returns detached copies.

**Remaining / acceptance:** Public mutable `QuestProgress`/`PlayerQuestProgress` functions remain usable by trusted Java callers. Enforce service ownership before exposing any network path; test inconsistent loaded definitions.

### C-004 — Return types and missing-value conventions

**Status:** `[~]` **P2**

**Current evidence:** The code uses `Optional` lookups and nullable internal `activeQuestId`; public runtime reads return detached copies.

**Remaining / acceptance:** Define immutable client-facing DTOs and consistent not-found/finished-campaign semantics during networking/UI work.

### C-005 — Define failure/error reporting beyond `boolean`

**Status:** `[~]` **P2**

**Current evidence:** `CampaignAdvancementResult` and `RewardClaimResult` now distinguish failures (`MAIN_OBJECTIVES_INCOMPLETE`, `NOT_FOUND`, `ALREADY_CLAIMED`, `CLAIM_IN_PROGRESS`, etc.).

**Remaining / acceptance:** Replace remaining ambiguous booleans only when a real UI feedback requirement calls for it; consider a structured inventory-full response.

### C-006 — Decide API naming and package conventions before wider release

**Status:** `[ ]` **P3**

**Current evidence:** Java package `com.TheAgeOfMachinery` and mod ID `theageofmachinery` are deliberately retained and build successfully.

**Remaining / acceptance:** Decide whether lowercase-package migration is worth the breaking change before public APIs; never silently rename the package.

### C-007 — Package-private access is encapsulation, not a security boundary

**Status:** `[~]` **P1**

**Current evidence:** `PlayerRewardInventory.claimReward()` was made public for storage-layer controlled claiming; this is Java visibility, not permission validation. Runtime claims bind to `ServerPlayer`, check server/session, and operate through a guarded claim path.

**Remaining / acceptance:** Keep direct domain-object mutation away from UI/network code; consider stricter module/package boundaries. Test packet-origin authentication when client requests exist.

### C-008 — Sealed reward hierarchy extensibility decision

**Status:** `[ ]` **P2**

**Current evidence:** `RewardDefinition` is sealed to `ExperienceReward` and `ItemReward`, fitting the current Create-focused design.

**Remaining / acceptance:** Revisit only if genuine addon-extensible reward providers are required; avoid speculative abstraction.

---

## 7. NeoForge, Create, runtime and tests

### I-001 — NeoForge 1.21.1 and Create+ version compatibility

**Status:** `[~]` **P1**

**Current evidence:** The build/runtime target is Minecraft 1.21.1, NeoForge 21.1.233, Java 21. The dev client and Create+ Modrinth profile are separate environments; core tests passed in the developer setup.

**Remaining / acceptance:** Verify actual Create and Create+ versions/dependencies before enabling Create event hooks or publishing compatibility claims; test with the real modpack.

### I-002 — Detect real Create engineering achievements

**Status:** `[ ]` **P1**

**Current evidence:** No functional Create machinery achievements are detected. Only manually updated prototype objectives exist.

**Remaining / acceptance:** Choose evidence for real Create outcomes (processing, rotations, transport, contraptions, automated cycles) and avoid accepting mere block placement as functional automation.

### I-003 — Multiplayer/session isolation

**Status:** `[~]` **P1**

**Current evidence:** UUID-keyed states, per-server runtime lifecycle, automatic login initialization, same-server/online player checks, and `/tamruntime audit` were implemented. The user successfully completed available 2.11.6 tests but **could not run a two-player test**.

**Remaining / acceptance:** **PENDING — NOT TESTED:** connect two accounts to the same server. Verify different objective counts, independent rewards, joining/rejoining, one player finishing while the other is active, and world isolation. Do not mark complete based on one-player checks.

### I-004 — Automated tests

**Status:** `[~]` **P1**

**Current evidence:** Numerous operator-command manual integration tests passed: persistence, claims, staged advancement, stale revisions, read/write isolation, failed delivery, reentrancy, login and runtime validation.

**Remaining / acceptance:** Build a JUnit/GameTest or other repeatable automated suite for these invariants, including malformed NBT, off-thread access and adversarial packet handling. No automated suite has been verified.

### I-005 — Logging, diagnostics, and schema upgrades

**Status:** `[~]` **P2**

**Current evidence:** Debug commands provide substantial diagnostics; `CampaignSavedData` uses format v3 with codec validation and load compatibility branches for v1/v2/v3.

**Remaining / acceptance:** Create structured logging, save migrations/backups, failure reporting, and automated compatibility tests; ensure debug logs do not reveal hidden content to clients.

### I-006 — Documentation for a public GitHub portfolio

**Status:** `[~]` **P1**

**Current evidence:** A README and living corrections guide exist; updated to the 2.11.6 checkpoint. Local builds, command-driven tests, and architecture are documented.

**Remaining / acceptance:** Maintain docs after material design changes; add reproducible real-gameplay demos/screenshots, install/version instructions, verified multiplayer status, and explicit release gates.

### I-007 — Automatic crafting event tracking

**Status:** `[ ]` **P1**

**Current evidence:** No gameplay crafting listener has been implemented; `craft_crafting_table` progress is currently changed with an operator command.

**Remaining / acceptance:** In 2.12.1 detect server-side crafting output and quantity correctly, including shift-click/multi-result crafting if applicable, match only active objectives, and prove progression changes without debug commands.

### I-008 — Item acquisition semantics and anti-double-counting

**Status:** `[ ]` **P1**

**Current evidence:** Gathering Cobblestone/Logs currently requires simulated progress; Minecraft acquisition has several entry paths (pickup, crafting, inventory transfer, rewards).

**Remaining / acceptance:** In 2.12.2 choose an explicit event-vs-inventory-holdings rule, reconcile initial counts and reconnects, avoid counting the same items twice, and prevent items from the reward system inadvertently satisfying unintended acquisition objectives.

### I-009 — Objective event routing and registry targets

**Status:** `[ ]` **P1**

**Current evidence:** Future gameplay event listeners need a data-driven match against active objectives rather than `if (objectiveId.equals("craft_crafting_table"))`.

**Remaining / acceptance:** In 2.12.3 add an objective type and target registry ID or event-key routing system, bind it to `CampaignRuntime`, and ensure event listeners cannot access future quest definitions or arbitrarily award progress.

### I-010 — Playable vertical-slice verification

**Status:** `[ ]` **P1**

**Current evidence:** Existing two-quest campaign completes with operator-generated progress; no real actions advance objectives yet.

**Remaining / acceptance:** In 2.12.4 gather resources, craft requested items, manually advance, and claim earned rewards end-to-end without a debug progress command. Recheck save/rejoin behavior.

---

## 8. Persistence integration and recovery

### P-001 — Eliminate duplicate authoritative manager state

**Status:** `[x]` **P0**

**Current evidence:** 2.8.1 and 2.8.2 removed independent player maps from managers. Both share authoritative `CampaignSavedData`; 2.11 introduced one runtime per Minecraft server and registered one quest manager through the advancement service.

**Remaining / acceptance:** Maintain this ownership when adding objective event listeners and JSON reloading. Do not create parallel runtime managers.

### P-002 — Make dirty marking inseparable from trusted mutations

**Status:** `[x]` **P0**

**Current evidence:** `CampaignSavedData` uses detached snapshots, copy-on-write, revision-checked storage, one advancement commit, and guarded claiming. Legacy unconditional `storePlayerQuestProgress` and `storePlayerRewardInventory` were removed in 2.10.6B. Normal mutation/restart regression tests passed.

**Remaining / acceptance:** Keep all new mutation paths revision-safe, server-thread-bound, and `setDirty()`-aware. Separately track crash persistence under P-005.

### P-003 — Validate and migrate loaded world data safely

**Status:** `[~]` **P1**

**Current evidence:** Schema v3 is unchanged across the runtime refactors, and previous save contents survived repeated world-restarter tests. Version branches for v1/v2/v3 and codec-level checks exist.

**Remaining / acceptance:** Test migrations from actual v1/v2 saves, missing/wrong-typed NBT values, corrupted states, future version handling, changed JSON objective/reward definitions, backups, and recovery.

### P-004 — Verify reward claims persist across world restarts

**Status:** `[x]` **P0**

**Current evidence:** Milestone 2.7.8 confirmed unlocked, claimed and remaining XP/item rewards survive world restarts, reject repeat claims and retain indivisible full-inventory retry behavior. Manager-backed tests repeated these results through 2.11.6.

**Remaining / acceptance:** Maintain regression coverage, ideally automated; crash timing is a separate open item P-005.

### P-005 — Handle crash timing and exactly-once delivery

**Status:** `[ ]` **P0**

**Current evidence:** Reward claim state is updated in controlled server execution, but Minecraft inventory/XP saving and campaign `SavedData` are not one durable transaction. An exception after delivery side effects can also leave an uncertain entitlement.

**Remaining / acceptance:** Design supported recovery: possible `REVIEW_REQUIRED` / pending ledger, idempotent delivery/journaling, safe retry policy, and crash simulation. Do not claim exactly-once XP/item delivery until supported by tests.

### P-006 — Remove or gate administrator debug endpoints for release

**Status:** `[ ]` **P1**

**Current evidence:** Many level-2 debug commands remain, including reward grant/unlock and arbitrary objective increments. The 2.11.6 audit intentionally exposes quest status to operators.

**Remaining / acceptance:** Disable, remove, or gate test commands in production builds; keep test harnesses in dev-only configuration and avoid permission bypass. Check spoiler information is never sent to ordinary clients.

### P-007 — Ensure client confidentiality matches distribution model

**Status:** `[ ]` **P1**

**Current evidence:** Current client networking/UI are not implemented. Built-in quest/reward definitions are server-managed at runtime, but a client-installed JAR can expose any embedded content through file inspection.

**Remaining / acceptance:** Transmit only permitted quest state and earned rewards; establish the secrecy threat model for content bundled client-side and consider server-only content distribution if extraction resistance is required.

### P-008 — Stable history ordering and restored identifiers

**Status:** `[ ]` **P2**

**Current evidence:** Quest progress restoration uses map-based histories and does not explicitly persist chronological completion order; rewards use `LinkedHashMap` for group insertion order.

**Remaining / acceptance:** Choose chronological/campaign ordering for future history UI, save explicit ordering if needed, and verify stable IDs/order across JSON updates and restarts.

### P-009 — Reduce privileged mutation and diagnostic API exposure

**Status:** `[~]` **P1**

**Current evidence:** The runtime is the intended gameplay facade, but `CampaignSavedData`, manager constructors, domain mutators and some debug commands are still public Java APIs. The API is not a sandbox against arbitrary server-side code.

**Remaining / acceptance:** Avoid exposing raw mutation paths to network handlers, consolidate production service ownership, distinguish dev-only calls, and consider package visibility/restricted interfaces when a concrete misuse is identified.

---

## 9. Updated delivery sequence

1. **2.12 — Real Minecraft gameplay events (next):** 2.12.1 crafting detection, 2.12.2 item acquisition/anti-double-counting, 2.12.3 definition-driven objective routing, 2.12.4 a playable two-quest prototype without manual debug increments. Keep event updates server-owned and limited to the active quest. **The choice to advance remains manual.**
2. **2.13 — Data-driven content:** validated JSON campaign definitions, immutable IDs, registry-aware objective targeting, authored ingredient/hint metadata; migration/backups when changing saved objective IDs.
3. **2.14 — Create integration:** verify Create/NeoForge compatibility in the actual Create+ environment, detect meaningful engineering achievements and functional processing/automation.
4. **2.15 — Network and Quest/Rewards UI:** spoiler-safe payloads, Quest Journal, ingredients and three hint levels, manual advancement button, grouped Claim actions, inventory-full feedback.
5. **Release safety gates:** automated tests (I-004), **two-player dedicated/LAN tests (I-003)**, negative packet/security tests (Q-005), crash-delivery recovery policy (P-005), debug-command gating (P-006), version/migration tests (P-003), packaging and documentation.

**Vertical-slice target:** Gather 16 Cobblestone and optionally 8 Oak Logs → manually advance → craft a Crafting Table and optionally a Furnace → manually advance → claim XP/items. The present prototype definitions use those objectives; **automatic event detection is the missing link**.

## 10. Unresolved design decisions

- **Item acquisition semantics:** count pickups, increases in held inventory, distinct source events, or an explicit combination; how to avoid duplicates after crafting, dropping, reclaiming and reconnecting.
- **Crafting semantics:** result-stack quantities, shift-click batch crafting, server-only detection, and recipe vs output-item identity.
- **Objective data model:** type-specific targets and multi-step Create achievements; registry validation and future schema upgrades.
- **Crash recovery:** policy for uncertain delivery after side effects or process termination; no unverified promise of exactly-once grants.
- **Reward presentation metadata:** main vs optional origin, stable entitlement identity, zero-reward group semantics and historical definitions.
- **Content distribution/secrecy:** preventing UI/network spoilers is feasible; preventing JAR asset extraction may require server-exclusive data.
- **Data migrations:** updating definitions without invalidating saved active/completed quests or existing reward quantities.
- **Multiplayer evidence:** maintain as **pending** until two accounts can test independent progress and reconnect behavior.
- **Release access policy:** development commands must not ship unguarded, even if currently level-2 operator-only.

## 11. Revision log

| Date | Change |
|---|---|
| 2026-10-25 | Initial consolidation of quest/reward decisions and correction backlog. |
| 2026-10-26 | Expanded reward and API issues through early reward implementation. |
| 2026-10-26 | Consolidated through 2.7.7, adding persistence issues P-001–P-008. |
| **2026-10-26** | **Updated through 2.11.6:** retained all 43 historical IDs, refreshed implementation/verification states after 2.7.8–2.11.6, recorded auto-login, unified runtime, staged/revision-checked persistence and failure tests; explicitly marked **two-player testing pending**; added event-routing tasks I-007–I-010 and API-boundary follow-up P-009; prioritized the 2.12 gameplay vertical slice. |

**Maintenance rule:** If work introduces an important deferred correction, add it to this guide with an acceptance test. Do not close an issue solely because compilation succeeds, and never mark a two-player or crash test as passed without running it.
