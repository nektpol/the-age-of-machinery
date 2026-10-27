# The Age of Machinery — Future Corrections & Implementation Guide

**Project:** The Age of Machinery  
**Target:** Minecraft 1.21.1 · NeoForge 21.1.233 · Java 21 · official Create 6.0.10 integration target (dev setup unverified)  
**Mod ID:** `theageofmachinery` · **Java package:** `com.TheAgeOfMachinery`  
**Document:** Living engineering backlog and architecture decisions  
**Last consolidated:** **27 October 2026 — 2.14 implementation plan and dependency research; 2.13.6 last completed milestone**

> **Status precision:** This ledger reflects developer-reported builds, commits and selected manual in-game checks; it is not an independent source audit or complete automated test report. Data-pack loading and text-only live reload are implemented, and screenshots confirmed both accepted cosmetic changes and rejected requirement changes. A test data pack with `"requiredAmount": 0` **reportedly caused a game failure without an actionable visible explanation**; the actual stack trace and startup-vs-live-reload circumstances have not been provided. This is an **open diagnostics/stability issue**, not a passing malformed-input test. Item pickups also produce frequent quest-progress chat messages; this is temporary until the Quest Journal is implemented. Official Create 6.0.10 / NeoForge 1.21.1 dependency coordinates are identified, but have **not** yet been integrated or build-tested in this project. **Two-player verification is PENDING / NOT TESTED.** Crash-atomic reward delivery and cross-restart definition compatibility are not established.

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
| **2.11** | Per-server runtime, login initiation, player-bound/server-thread authority | Developer reports completed/committed; two-player test NOT performed |
| **2.12.1–2.12.4** | Vanilla crafting/pickup events, event router, playable prototype scenarios | Builds/commits reported; some event edge-case logs still not independently available |
| **2.13.1** | Versioned JSON schemas, ordered manifest, prototype data | Build and commit reported successful |
| **2.13.2** | Strict JSON parser, Java model conversion, `QuestIngredient` metadata | Build and commit reported successful; nullability fix included |
| **2.13.3** | Manifest-based assembler, reference/item registry checks | Build and commit reported successful |
| **2.13.4** | Server data-pack loader feeding live `CampaignRuntime` | Build and commit reported successful; data-pack loading exercised |
| **2.13.5** | Presentation-only compatible `/reload`, structural rejection | In-game screenshots showed **APPLIED** title override and **REJECTED** 16→32/32→16 requirement changes; code ultimately built |
| **2.13.6** | End-to-end campaign/data-pack test milestone, third-quest fixture and invalid-JSON scenario | **Developer reports build and commit successful**; specific third-quest and optional-reward outcomes not independently supplied; `requiredAmount: 0` caused a failure without useful visible diagnostic |

**Next phase:** **2.14.1** integrates the verified Create 6.0.10 dependency and proves a functioning development setup. Create gameplay objectives remain unimplemented until subsequent milestones.

**Current state:** Minecraft server loads versioned JSON definitions (ordered campaign manifest, quests, ingredient checklists, rewards), parses and validates a complete campaign, then exposes it through one `CampaignRuntime` sharing `CampaignSavedData` v3. Gameplay `PICKUP_ITEM`/`CRAFT_ITEM` events match active objectives; advancement and reward claiming remain manual through operator commands until UI/network integration. `/reload` accepts presentation-only changes and refuses incompatible structural changes during the current server session.

**Known limitations:** The current session's reload validator **does not enforce cross-restart safety** for existing saves; a structurally different pack can take effect after restarting a world. Malformed pack failure messaging needs explicit remediation (I-011); matching item pickups currently spam chat/system notifications (I-012). Multiplayer with two connected accounts remains untested; bulk/shift-click crafting and other pickup pathways require regression coverage; exact-once crash-safe reward delivery is not established.

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

**Current evidence:** JSON parser validates resource identifiers; `ObjectiveTrigger` uses `ResourceLocation`; router compares actual registry item IDs, while assembly checks registered item targets, rewards and ingredient items. JSON IDs for the original two quests/objectives/rewards were preserved. Item tags are checked during active data-pack loading. Quest/objective/reward identity is still stored by stable strings.

**Remaining / acceptance:** Test missing optional Create registries, changed/deleted resource IDs, malformed pack failures (I-011), old world migration (P-003) and legacy reward identifiers. A valid-looking registry ID is not proof its mod is installed.

### D-003 — Objective types and event matching

**Status:** `[~]` **P1**

**Current evidence:** `QuestObjective` supports trigger and structured ingredient metadata; legacy four-/five-argument constructors are retained. `MANUAL`, `CRAFT_ITEM` and `PICKUP_ITEM` are parsed from JSON and routed via current-quest `CampaignObjectiveRouter`. Built-in game events identify crafting output and successful ground pickups; the optional Furnace objective is connected.

**Remaining / acceptance:** Verify bulk/shift-click crafts, partial pickups, duplicate pickup semantics, and the third-quest Chest event in a recorded run. Define trustworthy Create-specific event types in 2.14 rather than accepting item possession as proof of machinery automation.

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

**Current evidence:** The project targets Minecraft 1.21.1, NeoForge 21.1.233, Java 21. The **official Create NeoForge 1.21.1 addon guide** specifies **Create 6.0.10** (Maven version `6.0.10-280`), Ponder `1.0.82`, Flywheel `1.0.6`, Registrate `MC1.21-1.3.0+67`, plus a required production dependency range `[6.0.10,6.1.0)`. Official reference: https://wiki.createmod.net/developers/depend-on-create/neoforge-1.21.1. This is **documentation verification only**, not a report that these dependencies are already installed or that Create+ has been tested.

**Remaining / acceptance:** Milestone 2.14.1: integrate dependencies into this project's Gradle + NeoForge mod metadata, build, run a dev client with Create present, operate a small powered mechanism, and regression-test the existing JSON campaign. Inspect the actual installed Create version and supporting mods in the user's Create+ profile; verify separately in that profile and eventually on a server before claiming modpack compatibility. Pin dependency versions rather than assume the latest Maven snapshot is a release-equivalent artifact.

### I-002 — Detect real Create engineering achievements

**Status:** `[ ]` **P1**

**Current evidence:** Vanilla crafting and pickup objectives are now functional through 2.12. No Create processing, machines, networks, contraptions or functional engineering milestones are detected yet.

**Remaining / acceptance:** Follow 2.14.2–2.14.7: define event/provenance and player-credit policy, detect powered rotation, verify successful pressing, extend to other processing and repeated automated operations, then test the Create-focused JSON quest chapter. Avoid interpreting block placement or item possession as proof of a functional machine. See I-013 and I-014.

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

**Current evidence:** README and this ledger are updated through 2.13.6. The developer reports successful builds and commits for JSON schema/parser/assembler, startup loader, safe reload and final testing milestone. Screenshots show accepted cosmetic and rejected structural live reload. New observations for invalid-pack error feedback and frequent pickup announcements are recorded as I-011 and I-012.

**Remaining / acceptance:** Collect reproducible logs (including invalid-pack stack trace), example screenshots/video, a validated third-quest test report, exact Create dependencies/install instructions and release criteria. Keep build success distinct from verified game behavior.

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

**Remaining / acceptance:** Item-event routing for the existing prototype is implemented. New Create event types, JSON schema/parser validation and edge-case tests remain independently tracked in D-001, D-003, I-007 and I-008.

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

**Status:** `[~]` **P1** · **Policy agreed; implementation scheduled: 2.14.2 before automated machine objectives**

**Problem:** Kinetic networks, presses and production lines may operate autonomously while their builder is elsewhere or offline. A nearest-player or item-holder heuristic cannot reliably identify whom to credit. Per-player quest state and server authority already exist, but there is no machine-to-player association policy.

**Agreed current policy (2.14.2): builder = owner.** Attribute supported machinery/installation ownership to the server-verified builder (initially one UUID). Do not use nearest-player or item-holder heuristics. The exact Create machine construction/placement hook and identity of multi-block installations must be verified before coding. When attribution is unreliable, do not credit an arbitrary player.

**Agreed future cooperative policy (deferred):** Introduce an item providing a registered area of effect for a shared workshop. Any player registered with the area counts as an **owner**; **any owner** can open or close new registrations. All registered owners whose **own active quest** has a matching objective receive progress independently for the same verified qualifying operation. Unregistered players and owners on unrelated quests do not receive progress. Registration is an intentional membership action, not mere proximity. Do **not** implement the registration item during the initial 2.14.2 integration.

**Architecture invariant now:** Treat ownership as a **set of player UUIDs** from the start, even when only the builder belongs to the set. Keep each owner's objective progress, advancement and rewards separate and server-authoritative. Deduplicate processing per event **per eligible player**; never introduce a single shared campaign-progress record.

**Open implementation rules:** Define a stable installation identity; persistence/reconstruction across restarts; dismantling and replacement; owner changes and registration closure/revocation; overlapping area registration; world/dimension boundaries, chunks loading/unloading, offline members, automated block placement, spoofing and permission checks.

**Acceptance for initial implementation:** A server-verified builder is the only initial owner and earns qualifying progress through the existing player-bound progression path; no nearby nonowner gets credit; restart and teardown semantics are tested. **Acceptance for future shared area:** For two registered owners on the matching active quest, one qualifying machine event advances both independently once; an unregistered player and a registered owner on a different quest receive no credit; any owner can open/close registration, and a nonowner cannot. **Two-player testing still pending** (I-003).

### I-014 — Create processing provenance, duplicate credit and high-frequency events

**Status:** `[ ]` **P1** · **Scheduled: 2.14.2–2.14.5**

**Problem:** A collected Create item or placed machine does not prove successful mechanical processing. Multiple mod hooks, output inventories and ground pickups might report the same physical operation; an automated production line could generate very frequent callbacks.

**Required decision:** Inspect actual Create 6.0.10 APIs for trusted processing completion evidence; define machine/recipe/output/amount context, batch semantics, count point, idempotency window and overflow/capping. Prefer event-driven verification over unbounded block-entity polling. Keep quest chat feedback separate from machine processing (I-012).

**Acceptance:** A real powered machine operation increments one eligible active objective exactly once; unpowered or canceled processing, `/give`, pre-made items and pickup of a machine output do not count as processing. Bulk batches and world reloads have documented behavior. No progress is awarded to future quests.


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

**Current evidence:** Permission-level-2 development commands remain, including arbitrary objective progress, campaign audit, parser/registry test, live-reload status, and hardcoded first/second reward claim aliases. JSON test pack can add a third quest without recompiling, but the debug claim command does not yet support an arbitrary quest ID.

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

1. **2.12 — Vanilla Minecraft gameplay events: completed as development milestones.** Crafting, successful ground pickups and active-quest objective routing are wired to the server runtime. Edge cases (shift-click crafts, non-ground acquisitions) are still tracked.
2. **2.13 — JSON/data-pack campaigns: developer reports complete through 2.13.6.**
   - **2.13.1:** versioned JSON schema, explicit manifest order, rewards and structured ingredients.
   - **2.13.2:** strict JSON parsing to existing quest/reward models; compatible `QuestObjective` constructors.
   - **2.13.3:** resolve all manifest entries and item registries before assembling an immutable candidate.
   - **2.13.4:** load the active server data packs during startup rather than using Java `BuiltInCampaign` definitions; preserve saved IDs.
   - **2.13.5:** accept only presentation/text changes in a running server session. Screenshots verified title update (`APPLIED`) and rejected 16↔32 quantity changes (`REJECTED`).
   - **2.13.6:** end-to-end JSON scenario and three-quest test pack; build/commit reported complete. **Open observation:** `requiredAmount: 0` produced an opaque game failure (I-011); third-quest and optional-reward scenarios still need archived step-by-step evidence.
3. **2.14 — Create engineering integration (NEXT; seven sub-milestones).**
   - **2.14.1 — Dependency/runtime baseline:** add official Create 6.0.10 Maven and required mod metadata, test dev client and verify actual Create+ profile; not yet implemented.
   - **2.14.2 — Objective contracts and attribution:** trusted machine evidence, server authority, server-verified builder ownership now; multi-owner area registration later (I-013), JSON trigger extensions and duplicate policy (I-014).
   - **2.14.3 — Kinetic power:** recognize a working rotational network, not a placed shaft/wheel.
   - **2.14.4 — Mechanical Press:** track completed pressing rather than Iron Sheet possession.
   - **2.14.5 — General processing/automation:** reusable millstone/crushing/mixing processing evidence, batches and deduplication.
   - **2.14.6 — Create chapter:** Create-focused JSON quest content with optional engineering challenges, recipe ingredients and manual advancement.
   - **2.14.7 — Integration verification:** real Create machinery, invalid/unpowered cases, restart, rewards and Create+ profile; note two-player test as pending until performed.
4. **2.15 — Spoiler-safe networking + Quest Journal.** Only synchronize permitted active/completed quest data and earned reward claims; add ingredient checklists, three hint levels, player-driven manual advancement/claiming, and **remove/gate per-item chat updates (I-012)**.
5. **Release gates:** invalid JSON diagnostic reliability (I-011), save definition version locking and controlled migrations (P-003), crash-safe reward recovery (P-005), automated tests (I-004), two-player isolation (I-003, still pending), debug-command restrictions (P-006), packaging, install instructions and Create+ compatibility.

**Important:** 2.13's implementation and local live-reload tests are not a claim that startup invalid data or structurally changed world saves are safe. Unsupported definitions must fail clearly, not be silently accepted; currently the error display needs improvement.

---

## 10. Unresolved design decisions

- **Invalid pack diagnostics (I-011):** determine whether the `requiredAmount: 0` failure occurs at startup, during live reload or both; capture `latest.log` and stack trace; design actionable resource/field/value messages and keep running state intact on reload rejection.
- **World-save definition compatibility (P-003):** persist campaign revision/fingerprint and handle non-cosmetic JSON changes across server restarts; specify migration/backups or explicit rejection.
- **Quest notification policy (I-012):** journal/HUD update cadence; optional one-time completion toast; developer-only verbose chat; normal server logs for errors, not routine item pickup spam.
- **Create dependency version:** official 1.21.1 guide targets Create 6.0.10 with Maven `6.0.10-280`; confirm actual Create+ profile before relying on addon internals.
- **Create ownership (I-013):** **decision reached:** builder is initial owner; future area-registration item makes all registered players owners and allows any owner to open/close registration; eligible owners receive independent progress only for matching active quests. **Implementation details remain:** registration persistence, overlap, teardown, offline progress, unknown builders and owner permissions.
- **Create processing provenance (I-014):** detect successful completed operations, not block placement or item acquisition; avoid duplicate credit from overlapping events and high-frequency outputs.
- **Create objective evidence:** automation and processing must be verified by trusted events and sustained operation where appropriate; choose recipe/process/transport/rotation semantics after checking real dependency versions.
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

**Maintenance rule:** If work introduces an important deferred correction, add it to this guide with an acceptance test. Do not close an issue solely because compilation succeeds, and never mark a two-player or crash test as passed without running it.
