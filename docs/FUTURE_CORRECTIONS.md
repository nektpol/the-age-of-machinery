# The Age of Machinery — Future Corrections & Implementation Guide

> **2.14.6 system test passed — 28 October 2026:** user-confirmed Water Wheel progress, manual advancement and earned reward claims complete this technical acceptance test. Automated build/parser/domain/schema/recipe/null checks also passed. **Campaign content is provisional and requires redesign from the beginning.** The opening Cobblestone-before-Crafting-Table sequence is invalid; final progression must cover the whole Create+ inventory and addons, not just the initially instrumented machines. Finish the mod systems first, then design the actual campaign (D-007 below). Broader integration risks remain open; this test does not approve the current campaign or establish full survival/batch/multiplayer coverage.

**Project:** The Age of Machinery  
**Target:** Minecraft 1.21.1 · NeoForge 21.1.233 · Java 21 · Create 6.0.10 · Create+ 6.0.0-alpha-f  
**Mod ID:** `theageofmachinery` · **Java package:** `com.TheAgeOfMachinery`  
**Document:** Living engineering backlog, risks, verification ledger and architecture decisions  
**Last consolidated:** **28 October 2026 — 2.14.7 ownership accepted; 2.15 next; final campaign design deferred**

> **Status precision:** Developer reports a successful 2.14.4 build, 100% test success and a Git commit. The source/commit SHA and complete test output were not independently inspected for this documentation update. 2.14.1–2.14.3 were also reported built and committed. The full Create+ development run through Gradle now works after a local Java-environment correction whose specific changes were not recorded. **Two real-player multiplayer, crash-atomic reward claims, cross-restart campaign migration, invalid JSON diagnostics, automated performance/stress coverage and all-recipe batch semantics remain unverified**. A completed milestone does not automatically close other pending release risks.

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
| **2.1–2.4** | Quest state, objectives, per-player progression, reward models | Built; earlier domain tests reported |
| **2.5–2.6** | Actual XP/item delivery, full-inventory refusal, individual claims | In-game delivery, refusal/retry and duplicate rejection previously passed |
| **2.7.1–2.7.8** | `CampaignSavedData` NBT (`FormatVersion = 3`), quest/reward codecs | Normal restart restoration previously verified |
| **2.8–2.9** | Authoritative saved state and coordinated advancement | Completion, optional reward and restart checks previously passed |
| **2.10** | Staged updates, detached snapshots, revision/claim guards | Stale updates, reentrancy and failure-injection checks previously passed |
| **2.11** | Per-server runtime, login initialization, player/server-thread authority | Completed/committed; two-player test NOT performed |
| **2.12.1–2.12.4** | Vanilla crafting/pickup events and active-quest router | Built/committed; some crafting/pickup edge cases remain |
| **2.13.1–2.13.4** | JSON schemas, strict parser, ordered assembler and server data-pack loader | Builds/commits and data-pack activation reported |
| **2.13.5** | Presentation-only compatible `/reload`, structural rejection | In-game accepted cosmetic and rejected quantity-change screenshots |
| **2.13.6** | JSON quest/data-pack end-to-end scenarios | Built/committed; invalid `requiredAmount: 0` raised opaque failure (I-011) |
| **2.14.1** | Create 6.0.10 dependency and Create+ development-runtime baseline | Completed; full-pack IDE `runClient` works after Java-environment correction |
| **2.14.2** | Create machine events, builder registration/persistence, owner attribution, `KINETIC_ACTIVE` / `MACHINE_PROCESS` schema + routing | Built/committed as a development milestone; two-user test pending |
| **2.14.3** | `CreateKineticMonitor` detects live running installed machines; `/tamkinetic inspect` | Built/tested/committed (developer report) |
| **2.14.4** | Create 6.0.10 Mechanical Press Mixins for belt, world and basin; `ProcessingCompletedEvent` routed with machine ownership | **Build 100% successful, milestone tests 100% successful, committed (developer report)** |
| **2.14.5** | Millstone, Crushing Wheels and Mixer processing adapters | **Implemented; user confirmed all objective progress and accepted basic testing (2026-10-28)** |
| **2.14.6** | Provisional chapter fixture, ingredients, optional rewards and generic operator claims | Technical smoke test accepted by user; campaign design not approved |
| **2.14.7** | Basic Create integration checkpoint | User confirmed ownership and approved moving on; new restart/continued-progress tests unreported, multiplayer still pending |
| **2.15** | Authorized player state, server/client synchronization, Advance/Claim requests and restrained feedback | NEXT; six steps in milestones.md. Journal UI remains 2.16 |

**Working state:** A Minecraft server loads JSON data-pack quest definitions through a validated ordered manifest; `CampaignRuntime` and `CampaignSavedData` v3 own authoritative per-player progression. Existing `PICKUP_ITEM` / `CRAFT_ITEM` triggers work for prototype quests. Create machine installation ownership feeds trusted machine events into `CreateObjectiveRouter`. `CreateKineticMonitor` emits qualifying kinetic events; Mechanical Press hooks report actual item production from belt, world and basin paths. The full Create+ 6.0.0-alpha-f development instance launches via Gradle `runClient` after the user's Java environment fix.

**Known limits:** In-session structural `/reload` rejection is not world-restart schema locking. Malformed campaign diagnostics (I-011), chat spam (I-012), two-player testing (I-003), automated test depth (I-004), robust processing idempotency/output provenance (I-014), reward crash atomicity (P-005) and generalization to non-press machines remain open. Create+ launch issue was fixed externally; preserve the local setup rather than attempting the prior speculative Connector workaround.

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

**Current evidence:** Runtime operations require `ServerPlayer` and the server thread; invalid amounts/IDs were rejected during 2.11.6. Real `PlayerEvent.ItemCraftedEvent` and `ItemEntityPickupEvent.Post` handlers now route matching current-quest events through `CampaignObjectiveRouter` and the runtime, rather than accepting client-provided progress.

**Remaining / acceptance:** Validate bulk crafting and item acquisition corner cases; enforce trusted event sources for new Create triggers. When packets exist, the client may request advance/claim but never submit arbitrary progress counts or target player UUIDs. Add replay/adversarial packet tests.

### Q-006 — Idempotent reward claims, atomic full-stack item delivery

**Status:** `[~]` **P0**

**Current evidence:** Whole-reward inventory preflight and retry were verified in-game. `executeRewardClaim()` rejects duplicate/reentrant claims, blocks conflicting writes, and updates the claim state without a post-delivery revision check. Failed and successful claim states survive restarts.

**Remaining / acceptance:** No durable atomicity between Minecraft XP/items and `SavedData`; callback exceptions after side effects are uncertain. Define recovery/quarantine policy and test oversized stacks, exotic inventory states, and repeated network requests (P-005).

### Q-007 — Per-player persistent save/load and consistency

**Status:** `[~]` **P0**

**Current evidence:** `CampaignSavedData` v3 persists per-player quest/reward state. Normal world restarts, claim restoration, return login and staged advancement were verified in earlier milestones; gameplay listeners in 2.12 reuse the same runtime persistence path.

**Remaining / acceptance:** Two-player verification is pending (I-003). Recheck world-restarter behavior after definition changes in 2.13. Test migration, corrupted data and definition-version compatibility (P-003).

---

## 4. Rewards, delivery and user-facing features

### R-001 — Reward definitions and entitlement inventory

**Status:** `[x]` **P1**

**Current evidence:** XP/item reward records, entitlements grouped by quest, immutable claim entries, optional reward calculations, SavedData persistence, and real Minecraft delivery are implemented. In-game tests cover unlocking, claiming, duplicate rejection, inventory-full refusal, and restart restoration.

**Remaining / acceptance:** Future reward types and client presentation are separate enhancements; crash recovery is tracked by P-005 and optional-source metadata by R-007.

### R-002 — Earned optional rewards are frozen at advancement

**Status:** `[x]` **P1**

**Current evidence:** `QuestRewardDefinition.calculateEarnedRewards()` selects optional rewards completed before advancing. The 2.12 prototype now has real pickup and crafting triggers for both optional objectives, while the advancement service still freezes rewards when the quest is finalized.

**Remaining / acceptance:** Preserve earned-only optional rewards in 2.13 JSON changes and `/reload`; document a gameplay test comparing a completed optional objective to an intentionally skipped one (I-010).

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

**Status:** `[x]` **P1** (loader/parser/core definition behavior; compatibility hardening tracked separately)

**Current evidence:** 2.13.1–2.13.4 added versioned JSON Schema files, quest/manifest examples, `CampaignJsonParser`, `QuestIngredient`, `CampaignDefinitionAssembler`, and `CampaignDataPackLoader`. The active runtime now uses server data-pack content, with a two-quest default manifest and a separate three-quest test pack authored without new Java definitions. 2.13.5 added compatible text-only live reload; 2.13.6 is reported built and committed.

**Remaining / acceptance:** Preserve schema/model compatibility and test adding new quests end-to-end on a clean world. **Do not interpret this issue as closing** startup validation diagnostics (I-011) or structural save compatibility across restarts (P-003); those require independent safeguards.

### D-002 — Stable IDs and Minecraft registry types

**Status:** `[~]` **P1**

**Current evidence:** JSON parser validates resource identifiers; `ObjectiveTrigger` uses `ResourceLocation`; vanilla routing matches registered item IDs and Create routing now matches machine block IDs, output item IDs and kinetic minimum-speed criteria. Assembly validates quest targets and reward/ingredient items; the original stable quest/objective/reward IDs were preserved. Item tags are checked during data-pack loading.

**Remaining / acceptance:** Test missing optional Create registries, changed/deleted resource IDs, malformed pack failures (I-011), old world migration (P-003) and legacy reward identifiers. A valid-looking registry ID is not proof its mod is installed.

### D-003 — Objective types and event matching

**Status:** `[~]` **P1**

**Current evidence:** `QuestObjective` supports structured ingredient metadata and trigger definitions. The vanilla `MANUAL`, `CRAFT_ITEM` and `PICKUP_ITEM` types are parsed from JSON and routed through `CampaignObjectiveRouter`. Milestone 2.14.2 added Create `KINETIC_ACTIVE` and `MACHINE_PROCESS` objective triggers with machine ID, minimum RPM or output item ID, routed through `CreateObjectiveRouter`. Real live kinetic and Mechanical Press processing events are now implemented in 2.14.3–2.14.4.

**Remaining / acceptance:** Verify bulk/shift-click crafts, partial pickups, duplicate pickup semantics and the historical third-quest Chest fixture in a recorded run. Extend the existing Create trigger model to newly supported machines in 2.14.5 and prove actual output amounts rather than accepting item possession as evidence of machinery automation (I-014).

### D-004 — Campaign ordering and registration guarantees

**Status:** `[~]` **P1**

**Current evidence:** A versioned manifest (`campaigns/main.json`) explicitly orders quest resource IDs. Whole-campaign assembly rejects unresolved references before the runtime registers the candidate. Compatible `/reload` keeps the active order and requirements unchanged; invalid replacements do not partially replace the live campaign.

**Remaining / acceptance:** Implement cross-restart persisted definition-version/fingerprint checks and migration policy for existing world saves (P-003). Test insertion/removal/reordering during an existing saved campaign, not only clean-world startup. Never disclose full ordering to ordinary clients.

### D-005 — First-use ingredient checklist

**Status:** `[~]` **P1**

**Current evidence:** Versioned quest JSON supports structured ingredient entries with item/tag IDs and positive amounts; `QuestIngredient` metadata is retained in `QuestObjective`. The prototype uses planks for Crafting Table and Cobblestone for Furnace. The active data-pack loader validates item ingredients and populated item tags.

**Remaining / acceptance:** Build the actual player-facing ingredient checklist when the active quest first introduces a craft; allow valid recipe alternatives/tags and Create multi-stage recipes, keep the checklist spoiler-scoped, and avoid confusing authored material hints with real recipe guarantees. UI is planned for 2.15.

### D-006 — Progressive hints without spoilers

**Status:** `[ ]` **P1**

**Current evidence:** Three progressive hint levels are a confirmed product requirement; no hint system is implemented.

**Remaining / acceptance:** Add per-objective permitted hint tiers and server-controlled disclosure. Avoid future quest or reward leaks and use recipe/Ponder guidance only when permitted.

---

### D-007 — Redesign the final campaign for the whole Create+ inventory

**Status:** `[ ]` **P1** — deliberately deferred until the mod systems are complete (user decision, 2026-10-28).

**Observed:** The default quest asks for 16 Cobblestone before crafting a Crafting Table, although obtaining Cobblestone normally requires a pickaxe made using that table. The current prelude and six-quest workshop chapter are provisional system fixtures. Passing 2.14.6 validates quest progression/advancement/rewards, not the campaign design.

**Acceptance:** After the mod systems are complete, inventory the full installed Create+ pack and its addons, map actual recipe/build dependencies, and design progression from the beginning. Cover the whole Create+ inventory rather than only the few machines currently hooked. Ensure prerequisite tools and workstations precede tasks that require them, avoid pointless recrafting/grinding, and review the proposed campaign with the user before treating it as final. Do not require a playthrough of the rejected opening as an integration-test gate.

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

**Status:** `[~]` **P1** — working development integration; release/server compatibility still needs verification.

**Current evidence:** Minecraft 1.21.1, NeoForge 21.1.233 and Java 21 target. The official Create dependency target is 6.0.10 (Maven `6.0.10-280`) with Ponder 1.0.82, Flywheel 1.0.6 and Registrate `MC1.21-1.3.0+67`. The full Create+ modpack (6.0.0-alpha-f) runs via isolated Gradle `runClient` and supports IDE source debugging after a separate agent fixed a **Java-environment issue**. Earlier `forgeclientdev` / Sinytra Connector `Could not determine clean minecraft artifact path` failures are historical troubleshooting, **not a current open blocker**. The details of the external Java fix were not captured in this record.

**Remaining / acceptance:** Preserve working `build.gradle`, `settings.gradle`, JDK/Gradle options and `run-createplus` task setup; record the exact Java resolution for onboarding and CI. Verify a clean checkout, a dedicated server and the distribution JAR against the actual modpack. Keep the original Modrinth profile untouched; do not substitute manual JAR copying for full Gradle development.

### I-002 — Detect real Create engineering achievements

**Status:** `[~]` **P1** — 2.14.3 kinetic and 2.14.4 Mechanical Press completed; other machines and sustained automation pending.

**Current evidence:** `KINETIC_ACTIVE` and `MACHINE_PROCESS` trigger contracts exist. `CreateKineticMonitor` checks registered kinetic installations approximately every 10 server ticks and routes valid `KineticPowerEvent` events; press Mixins capture actual belt/world/basin item production and send `ProcessingCompletedEvent` through the ownership router. The developer reports 2.14.4 built, fully tested and committed.

**Remaining / acceptance:** 2.14.5 implements Millstone, Crushing Wheels and Mechanical Mixer with accurate quantities and distinct callbacks. 2.14.6–2.14.7 create a production-focused JSON chapter and verify power loss, bulk batches, restarts, multi-machine throughput, output side effects and modpack compatibility. Item possession or machine placement still cannot count as successful processing. See I-013 and I-014.

### I-003 — Multiplayer/session isolation

**Status:** `[~]` **P1**

**Current evidence:** UUID-scoped save data, per-server runtime, login initialization, same-server/online-player checks and `/tamruntime audit` are implemented. The developer **cannot currently perform a two-player test**. The 2.12 objective router uses actual `ServerPlayer` identity but does not itself verify simultaneous player isolation.

**Remaining / acceptance:** **PENDING — NOT TESTED:** connect two accounts to one dedicated/LAN server. Verify independent pickup/crafting counts, quest transitions, earned rewards, reconnect, and world isolation. Do not treat single-player checks as a substitute.

### I-004 — Automated tests

**Status:** `[~]` **P1**

**Current evidence:** Manual debug commands cover parser validation (`/tamjson test`), registry assembly (`/tamregistry test`), compatibility (`/tamreloadtest`), runtime validation and in-game reload checks. Data-pack fixtures for cosmetic/incompatible reload and a third quest exist. No independently verified automated JUnit/GameTest/CI suite exists.

**Remaining / acceptance:** Automate parser file/field errors, invalid-pack startup/reload behavior (I-011), actual progress event counts, optional reward scenarios, history save migrations, partial pickups, rapid/shift-click crafting, off-thread access, malicious packets and two-player isolation when test resources permit.

### I-005 — Logging, diagnostics, and schema upgrades

**Status:** `[~]` **P2**

**Current evidence:** Save format v3 has codec validation; runtime diagnostics include reload status and rejection reason. Screenshots confirmed informative **structural compatibility rejection** (`Required quantity changed`) during live reload, but a pack with `requiredAmount: 0` reportedly caused a game failure without a useful visible explanation.

**Remaining / acceptance:** Keep authoritative server logs and player chat separate. Provide structured errors identifying resource, JSON field and violation, with stack trace/causes retained in logs for genuine unexpected exceptions (I-011). Never dump secret future quests/rewards into ordinary player UI; retain server-only diagnostics.

### I-006 — Documentation for a public GitHub portfolio

**Status:** `[~]` **P1**

**Current evidence:** README and this ledger are now updated through 2.14.4 (using the 2.14.2 documentation snapshot plus reported newer milestones). The developer reports successful builds and commits for JSON schema/parser/assembler, startup loader, safe reload and final testing milestone. Screenshots show accepted cosmetic and rejected structural live reload. New observations for invalid-pack error feedback and frequent pickup announcements are recorded as I-011 and I-012.

**Remaining / acceptance:** Collect reproducible logs (including invalid-pack stack trace), example screenshots/video, committed 2.14.4 test evidence, the exact external Java fix for full Create+ development, clean-checkout/server install instructions and release criteria. Keep build success distinct from verified game behavior.

### I-007 — Automatic crafting event tracking

**Status:** `[~]` **P1**

**Current evidence:** 2.12.1 implemented `CampaignCraftingEvents` from `PlayerEvent.ItemCraftedEvent`; 2.12.3 removed hardcoded item checks and routes `CRAFT_ITEM` objectives by registry ID. Prototype Crafting Table and Furnace objectives are connected.

**Remaining / acceptance:** Capture an explicit in-game regression for shift-click/rapid/bulk crafting and produced item counts; verify no duplicate event credit and no progress for earlier/future quests. The developer reported builds and commits, but not separate detailed edge-case results.

### I-008 — Item acquisition semantics and anti-double-counting

**Status:** `[~]` **P1**

**Current evidence:** 2.12.2 implemented successful ground pickup detection using `ItemEntityPickupEvent.Post` and `originalStack.getCount() - currentStack.getCount()`; routing matches active-quest `PICKUP_ITEM` objectives. This counts actual ground-item transfers and caps progress, but does not inspect all inventory acquisition methods.

**Remaining / acceptance:** Confirm partial-stack pickups and anti-double-counting. Decide whether chest transfers, crafted outputs, grants and repeated drop/re-pickup count. Distinguish **obtain** from **personally mine**; define anti-farming semantics if required. Do not imply current pickup coverage includes all item acquisition.

### I-009 — Objective event routing and registry targets

**Status:** `[x]` **P1**

**Current evidence:** 2.12.3 introduced `ObjectiveTriggerType`, `ObjectiveTrigger` with validated registry identifier, five-argument `QuestObjective` plus compatible four-argument constructor, and `CampaignObjectiveRouter`. Crafting and pickup event adapters no longer hardcode quest/item IDs. The active quest alone is evaluated; matching objectives use the shared `CampaignRuntime` update path.

**Remaining / acceptance:** The vanilla active-quest item-event router is implemented. A separate Create event router with `KINETIC_ACTIVE` and `MACHINE_PROCESS` exists as of 2.14.2; general multi-machine provenance, edge-case/automated tests and count semantics remain tracked in D-003, I-002, I-014 and I-015.

### I-010 — Playable vertical-slice verification

**Status:** `[~]` **P1**

**Current evidence:** The two-quest playable crafting/pickup progression path is implemented and milestones 2.12.4 and 2.13.6 were reported built and committed. JSON-defined gameplay and compatible reloads were exercised; the detailed full/optional-skipped/third-quest checklists have not been supplied as one complete test record.

**Remaining / acceptance:** Archive scenario-by-scenario results in `docs/tests`, including zero debug-progress commands, forfeited optional rewards, third-quest Chest crafting, reward persistence and malformed-pack failure specifics. Close only after verifying actual gameplay behavior rather than builds alone.

### I-011 — Actionable diagnostics for invalid campaign packs

**Status:** `[ ]` **P1** · **Escalate to P0 if malformed data can crash an already-running server during `/reload`**

**Observed (2.13.6):** A developer test data pack changed an objective to `"requiredAmount": 0`. The game/world reportedly failed without a useful visible explanation. The exact exception, log contents, and whether failure was on world startup or an in-session reload have **not yet been inspected**. This is an observed missing-diagnostic/stability issue, not evidence that malformed content was safely rejected in all paths.

**Required behavior:** Invalid definitions must **never be accepted silently**. For live `/reload`, retain the previous valid campaign and report `REJECTED` with a meaningful cause. For initial startup when no valid campaign exists, fail clearly and predictably with an actionable server log/loader error rather than a mysterious crash or an undocumented fallback. Include resource path/ID, JSON field (e.g., `objectives[0].requiredAmount`), offending value (`0`), violated rule (positive quantity) and underlying exception if unexpected. Avoid exposing unrevealed quest data to non-operators.

**Acceptance tests:** Test valid JSON with invalid values (`0`, negatives, missing fields), syntactically invalid JSON, missing quest files, invalid registry IDs and startup vs in-session reload. Confirm precise diagnostics in server log/latest.log and useful operator-facing reload status; confirm live state and saved progress remain untouched on rejected reload. Collect the actual crash stack trace before choosing the corrective implementation.

### I-012 — Replace per-item quest chat spam with UI-driven progress feedback

**Status:** `[ ]` **P1** (current behavior accepted temporarily during UI-less development)

**Observed (2.13.6):** `CampaignObjectiveRouter` currently sends quest-update chat/system messages when each matching ground-item pickup or craft event advances an objective. Rapid pickups can flood the player's chat even though progression itself is correct.

**Required behavior:** When the Quest Journal/client state sync is implemented (2.15), **stop sending a chat line for each item pickup**. Present current objective quantities and completed states in journal/HUD UI using authorized server-to-client state or deltas. Optionally send one restrained notification on objective completion or significant milestones, not on every item event. Make verbose progress chat developer-only/configurable if it remains useful for testing. Preserve separate `LOGGER` error/warning output for administrators; disabling chat feedback must **not** suppress validation diagnostics.

**Acceptance tests:** Pick up many one-item stacks and a large stack; no chat flooding in normal play, while progress remains accurate and UI updates within the expected sync cycle. Confirm completion notification happens once, completed/future objectives do not leak, reconnect restores display state and multiple matching objectives behave consistently. Test UI-disabled/dev logging separately.


### I-013 — Create machinery ownership and player progress attribution

**Status:** `[~]` **P1** — initial builder-as-owner implementation complete; cooperative area registration and two-player tests deferred.

**Implemented (2.14.2):** Persist a stable machine installation/position association with the server-verified placing player's UUID as initial owner. `MachineOwnershipRegistry` and `MachineOwnershipSavedData` manage registration/persistence; `MachineLocation` identifies position/dimension; `CreateEventAttribution` validates the recorded installation and resolves the **set of owner UUIDs**, and `CreateObjectiveRouter` matches each owner's own currently active quest. The existing server-authored commands `/tamowner` and `/tamcreate` support diagnosis and synthetic event tests. Do not use nearest-player, item-holder or output collector heuristics.

**Current ownership policy:** Machine ownership is independent from quest/reward state and can contain multiple UUIDs internally, even when the first stage normally registers one builder. Each qualifying owner independently receives progress when their active objective matches; do not advance future quests or use a shared global counter. Unknown/unregistered machines do not receive arbitrary credit. Ownership teardown/replacement and chunk/world lifecycle behavior deserve repeated regression during 2.14.7.

**Agreed future cooperative feature (deferred):** A registration item establishes a shared bounded workshop area. Any registered player becomes an owner; **any owner** can open/close registration. Credit goes to each eligible registered owner only if their currently active quest contains a matching objective. Registration is intentional and not inferred from proximity. Design overlaps, owner removal, closure, permission enforcement, offline owner progress, unloaded chunks and multi-block installations explicitly.

**Remaining / acceptance:** Confirm save/restart, dimension separation, unknown-builder placement, break/replacement, chunk unload and fake/detached BlockEntity scenarios. **Two simultaneous real-user verification remains pending:** two owners on the same relevant quest receive independent credit exactly once, nonowners receive none, and owners on unrelated quests receive none (I-003). No existing single-user test closes that requirement.

### I-014 — Create processing provenance, duplicate credit and high-frequency events

**Status:** `[~]` **P1** — real Mechanical Press belt/world/basin item outputs implemented and developer-reported tests pass; wider correctness not complete.

**Implemented (2.14.4):** Mixins against Create 6.0.10 report successful belt processing (`tryProcessOnBelt`, nonsimulated output list), loose-world-item pressing (`onItemPressed`), and successful basin item output acceptance with press-specific context (`BasinBlockEntity.acceptOutputs`). `MechanicalPressProcessingBridge` validates server/current installed machine and groups produced item quantities into `ProcessingCompletedEvent` entries; `CreateObjectiveRouter` attributes only to registered matching owners. Belt handling avoids counting both belt result and Create's `onItemPressed` callback. User reports successful build, 100% tests and commit.

**Open edges/risks:**

- **Operation identity:** The 2.14.4 code initially supplied constructed `UUID.randomUUID()` for each emitted output event. Session deduplication of event IDs cannot prevent two independent callbacks about the *same physical operation* from having different IDs. Before promising exactly-once operation semantics, inspect the final committed code and design stable per-operation identity or unequivocal single emission points.
- **Basin safety:** The supplied bridge tracked the active press with a `ThreadLocal` set by `tryProcessInBasin` HEAD and removed at RETURN, not in `try/finally`. Exception/nested-call cleanup and overlapping basin operations require inspection or a safer scope design. Confirm current committed code before changing it.
- **Output fidelity:** Loose-world-item `onItemPressed` reports a primary output; verify byproducts, recipe chance outputs, consumed items and empty lists. Basin output acceptance and actual successful transfer may differ; inspect Create internals before asserting final delivered counts.
- **Batch/replay:** Test bulk stack sizes, multiple output types, rapid cycles, save/restart around operation boundaries, aborted processing, config switches, high throughput and stress changes without double counting or missed events.
- **Credit boundaries:** Fabric/NeoForge addons may alter Create recipes, inventories or callbacks. Item pickup, `/give`, machine placement and simulated processing must not be reported as machine completion. Keep chat-notification volume tracked separately in I-012.

**2.14.5 acceptance:** Establish machine-specific authoritative recipe success points for Millstone, Crushing Wheels and Mechanical Mixer, normalize output quantity semantics, avoid a second progress engine, prove machine owner isolation, and write explicit acceptance tests for automation/batches. Preserve server-thread checks and no-unloaded-chunk world access.

### I-015 — Press-to-general-processing integration robustness

**Status:** `[ ]` **P1** — technical review before treating the processing bridge as a reusable abstraction in 2.14.5.

**Problem:** Generalizing 2.14.4 by copying Mixins and callbacks risks leaking basin context, reporting canceled/failed recipes, or counting multiple output types with overlapping event IDs. The source of truth for processing completion varies among Create machines.

**Required approach:** Before refactoring, inspect the **committed** `MechanicalPressProcessingBridge`, `MechanicalPressBlockEntityMixin`, `BasinBlockEntityMixin` and Mixins configuration. Define a shared immutable success-event payload only after verifying Create 6.0.10 execution points. Keep press behavior stable under regression while adding new machine-specific adapters. Choose a scope-safe basin attribution mechanism (including `try/finally` if appropriate), explicit output counts, and trustworthy operation identity. Do not assume a Java `ThreadLocal` automatically provides lifecycle cleanup.

**Acceptance:** The existing 2.14.4 press tests remain green, new machines produce correct counts once, no failed/simulated output is reported, and no stale press/mixer basin context crosses callbacks. Review this issue together with I-014; do not prematurely claim it closed based only on a build.

---

## 8. Persistence integration and recovery

### P-001 — Eliminate duplicate authoritative manager state

**Status:** `[x]` **P0**

**Current evidence:** `QuestManager` and `RewardManager` share authoritative `CampaignSavedData`; one per-server `CampaignRuntime` manages the services. The 2.12 crafting and pickup adapters route through the runtime, without creating parallel managers.

**Remaining / acceptance:** Keep the same service/storage ownership during JSON loading and `/reload`; never activate a partially registered parallel campaign.

### P-002 — Make dirty marking inseparable from trusted mutations

**Status:** `[x]` **P0**

**Current evidence:** `CampaignSavedData` uses detached snapshots, copy-on-write, revision-checked storage, one advancement commit, and guarded claiming. Legacy unconditional `storePlayerQuestProgress` and `storePlayerRewardInventory` were removed in 2.10.6B. Normal mutation/restart regression tests passed.

**Remaining / acceptance:** Keep all new mutation paths revision-safe, server-thread-bound, and `setDirty()`-aware. Separately track crash persistence under P-005.

### P-003 — Validate and migrate loaded world data safely

**Status:** `[~]` **P0 before allowing structural campaign content updates in real saved worlds**

**Current evidence:** NBT v3 stores player progress by stable IDs; 2.13's loader assembles current data-pack campaign on server startup. During a running session, `CampaignReloadCompatibility` rejects structural differences and permits text changes. Screenshots confirmed this for changing `gather_cobblestone` quantity 16↔32.

**Remaining / acceptance:** **Critical limitation:** A world/server restart may load structurally changed JSON into the runtime without checking the definition set used when player data was last saved. Persist a campaign version/fingerprint or migration checkpoint, compare it to stored player history, reject unsafe startup changes with actionable errors, and offer explicit versioned migrations/backups when needed. Test additions, removals, reordered quests, altered quantities/rewards, removed mod registry content, corrupted NBT and old world versions. Keep this separate from reload validation and invalid-pack diagnostics (I-011).

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

**Current evidence:** Permission-level-2 development commands remain, including arbitrary objective progress, campaign audit, parser/registry test, live-reload status, and hardcoded first/second reward claim aliases. The 2.14.6 operator command `/tamruntime claim quest <questId> <rewardId>` now supports arbitrary earned quest rewards through the existing player-bound runtime; first/second aliases remain available.

**Remaining / acceptance:** Hide/remove or strictly dev-gate diagnostic commands before public release. Replace operator-mediated Advance/Claim with properly authorized client requests and server checks in 2.15; generic developer claim by quest ID may be useful for test coverage but must not be a public endpoint.

### P-007 — Ensure client confidentiality matches distribution model

**Status:** `[ ]` **P1**

**Current evidence:** Campaign JSON is parsed and kept authoritative in server runtime; the future client networking and journal are not implemented. Default quest JSON is bundled into mod resources, so its contents may be extractable from a distributed client JAR even if the runtime sends only permitted quest data. Server-only external data packs allow better content control.

**Remaining / acceptance:** Keep future quest/reward content out of unauthorized network payloads and ordinary UI; choose how confidential campaign data is distributed. Do not equate server-runtime secrecy with secrecy from JAR inspection.

### P-008 — Stable history ordering and restored identifiers

**Status:** `[~]` **P2**

**Current evidence:** The JSON campaign manifest now establishes deterministic *current* campaign order. Saved quest history continues to use map-based state rather than an explicitly persisted completion timeline; rewards use stable grouped storage.

**Remaining / acceptance:** Decide chronological vs manifest-based history ordering for the journal. Preserve stable saved IDs across text reloads; define handling for historical quests missing or moved in future campaign versions (P-003).

### P-009 — Reduce privileged mutation and diagnostic API exposure

**Status:** `[~]` **P1**

**Current evidence:** The runtime is the intended gameplay facade, but `CampaignSavedData`, manager constructors, domain mutators and some debug commands are still public Java APIs. The API is not a sandbox against arbitrary server-side code.

**Remaining / acceptance:** Avoid exposing raw mutation paths to network handlers, consolidate production service ownership, distinguish dev-only calls, and consider package visibility/restricted interfaces when a concrete misuse is identified.

---

## 9. Updated delivery sequence

1. **2.12 — Vanilla gameplay events (done as development milestones):** Crafting and successful ground pickup feed the active quest. Shift-click/bulk crafting and broader acquisition semantics remain separately tracked.
2. **2.13 — JSON/data-pack campaigns (completed 2.13.6):** Versioned schemas, strict parser, ordered validated assembly, server data packs and presentation-only in-session reload. Startup-invalid JSON diagnostics (I-011) and save definition compatibility across restarts (P-003) remain open.
3. **2.14.1–2.14.4 — Create integration (completed/committed by developer report):**
   - **2.14.1:** Create 6.0.10 dependencies, full Create+ 6.0.0-alpha-f isolated Gradle dev client; original Java launch blocker resolved by external agent.
   - **2.14.2:** MachineLocation/installation identity, builder registration/persistence, per-owner attribution, kinetic/processing event contracts and JSON triggers; routing/synthetic event test commands.
   - **2.14.3:** Server-side live kinetic monitoring; working/stressed/stationary distinctions; `/tamkinetic inspect`.
   - **2.14.4:** Mechanical Press real belt, loose-world-item and basin item-output callbacks; progress through existing routing; developer reports 100% tests and commit.
4. **2.14.5 — Additional processing/automation (basic progress accepted):** Millstone, Crushing Wheels, Mechanical Mixer. Inspect exact Create 6.0.10 code before choosing hooks; capture output item and quantity for *successful* operations, deduplicate operation provenance, handle batches and byproducts, and avoid extra inventory polling. Keep all 2.14.4 press regressions green; resolve or explicitly record I-014/I-015 edges.
5. **2.14.6 — Create quest chapter:** Author actual survival Create quest progress in JSON/data packs with first-use required ingredients, optional mechanical achievements, safe manual advancement and earned XP/item rewards. For schema or datapack edits, provide the **complete final `quest.schema.json` plus a ready-to-install full ZIP**, not isolated fragments.
6. **2.14.7 — Integrated Create+ tests:** Powered vs unpowered, overstressed/reverse rotation, machine ownership, processing outputs, multiple simultaneous machines, restart/rejoin, malformed definitions, quest optional handling/rewards and production profile. Two-account tests remain a separate explicit gate until available.
7. **2.15 — Journal and networking:** Spoiler-safe player-visible quest state, ingredient checklist with quantities, three hints, manual advancement/claim UI and no per-item chat flood (I-012).
8. **Release gates:** I-011 malformed input feedback, P-003 save definition migrations, P-005 crash-safe rewards, I-003 two-player isolation, I-004 automated tests, P-006 debug command protection and documented full-pack compatibility.

**Development constraint:** preserve direct IDE/debuggable Gradle `runClient` using isolated `run-createplus`. Avoid reintroducing manual JAR-to-Modrinth testing as the required development workflow. The exact externally fixed Java setting is unknown here; inspect project-local JDK/Gradle configuration before touching runtime setup.

## 10. Unresolved design decisions

- **Invalid pack diagnostics (I-011):** determine whether the `requiredAmount: 0` failure occurs at startup, during live reload or both; capture `latest.log` and stack trace; design actionable resource/field/value messages and keep running state intact on reload rejection.
- **World-save definition compatibility (P-003):** persist campaign revision/fingerprint and handle non-cosmetic JSON changes across server restarts; specify migration/backups or explicit rejection.
- **Quest notification policy (I-012):** journal/HUD update cadence; optional one-time completion toast; developer-only verbose chat; normal server logs for errors, not routine item pickup spam.
- **Create dependency version and development reproducibility (I-001):** official 1.21.1 guide targets Create 6.0.10 with Maven `6.0.10-280`. Full Create+ Gradle launch works locally following an externally made Java environment fix; record exact setting and verify on a clean checkout and dedicated server.
- **Create ownership (I-013):** **decision reached:** builder is initial owner; future area-registration item makes all registered players owners and allows any owner to open/close registration; eligible owners receive independent progress only for matching active quests. **Implementation details remain:** registration persistence, overlap, teardown, offline progress, unknown builders and owner permissions.
- **Create processing provenance (I-014/I-015):** press belt/world/basin output observation is implemented; generalize to Millstone/Crushing Wheels/Mixer with verified completion hooks, batch counts, stable operation identity and exception-safe basin context. Avoid duplicate credit from overlapping events or pickups.
- **Create objective evidence:** kinetic activity and the Mechanical Press are implemented; new automation must be verified by trusted recipe/output events and appropriate sustained operation in Create 6.0.10 rather than inferred from item possession.
- **Pickup semantics:** current ground pickups only. Decide whether chest transfers, item grants, repeat drop/re-pickup and other inventories should count, and prevent unintentional farming if necessary.
- **Crafting semantics:** ensure output count and shift-click/rapid recipe handling do not double-count.
- **Reward durability:** Minecraft XP/items and campaign `SavedData` are not a crash-safe single transaction; define a pending/review state or other recovery policy before promising exactly-once delivery.
- **Spoilers and distribution:** restrict future quest/reward data in network state and UI, but recognize bundled JAR files are inspectable; prefer server-only packs for truly confidential campaign content.
- **Progress history presentation:** ordering of completed quests and stable historical rewards/IDs under later content revisions.
- **Multiplayer evidence:** **PENDING** until two real players connect simultaneously and test independent progress/claims, reconnect and world isolation.
- **Release controls:** debug-only commands, test packs, error reporting and client permissions must be explicitly evaluated before a public build.

## 11. Revision log

| Date | Change |
|---|---|
| 2026-10-25 | Initial consolidation of quest/reward decisions and correction backlog. |
| 2026-10-26 | Expanded reward and API issues through early reward implementation. |
| 2026-10-26 | Consolidated through 2.7.7, adding persistence issues P-001–P-008. |
| **2026-10-26** | **Updated through 2.11.6:** retained all 43 historical IDs, refreshed implementation/verification states after 2.7.8–2.11.6, recorded auto-login, unified runtime, staged/revision-checked persistence and failure tests; explicitly marked **two-player testing pending**; added event-routing tasks I-007–I-010 and API-boundary follow-up P-009; prioritized the 2.12 gameplay vertical slice. |
| **2026-10-26** | **Updated through 2.12.4:** documented vanilla crafting/pickup events and objective trigger routing, revised I-007–I-010 and D-002–D-005 without renumbering existing issues, separated build/commit confirmation from missing detailed test logs, and added the six-step 2.13 JSON/data-pack roadmap and reload/save compatibility criteria. Two-player verification remains pending. |
| **2026-10-27** | **Updated through 2.13.6:** documented versioned JSON schemas, parser, manifest assembly, data-pack startup, safe cosmetic reload and manual integration tests; retained all 48 previous correction IDs and added **I-011** (invalid-pack diagnostic failure) and **I-012** (per-item quest chat spam); maintained two-player verification as pending and startup save-version lock as an open risk. |
| **2026-10-27** | **Planned 2.14 Create integration:** preserved all 50 prior issue IDs; documented verified official Create 6.0.10/Maven dependency versions without claiming installation, detailed the seven implementation milestones, and added **I-013** (machine-to-player attribution) and **I-014** (processing provenance/duplicate credit). Kept I-011, I-012, startup migration, two-player tests and reward crash timing open. |
| **2026-10-27** | **2.14.2 ownership decision:** builder = initial owner; design ownership as UUID set now. Future area-registration item supports many co-owners, owner-controlled registration, and independent same-quest progress. No multi-account test or implementation claimed. The 2.14.1 Gradle build and Create+ 6.0.0-alpha-f mod discovery were user-confirmed. |
| **2026-10-27** | **Updated through 2.14.4:** 2.14.2 contracts/ownership, 2.14.3 live kinetic monitoring and 2.14.4 Mechanical Press observation marked completed on developer report (successful build, 100% tests, committed). Recorded restored full Create+ Gradle debug workflow and external Java fix; made 2.14.5 next. Refreshed I-001, I-002, I-013, I-014, added I-015 for cross-machine processing bridge robustness; preserved other issue IDs and open multiplayer/crash/schema risks. |

**Maintenance rule:** If work introduces an important deferred correction, add it to this guide with an acceptance test. Do not close an issue solely because compilation succeeds, and never mark a two-player or crash test as passed without running it.
