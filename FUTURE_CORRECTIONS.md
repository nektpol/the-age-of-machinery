# The Age of Machinery — Future Corrections & Implementation Guide

**Project:** The Age of Machinery  
**Target:** Minecraft Java Edition 1.21.1 · NeoForge 21.1.233 (Create+ compatibility) · Java 21  
**Package currently used:** `com.TheAgeOfMachinery`  
**Mod ID:** `theageofmachinery`  
**Document type:** Living engineering backlog / architecture decision record  
**Last consolidated:** 2026-10-26 (Milestone 2.7.7; build and commit confirmed)  

> **Purpose:** Preserve every deferred correction, limitation, and planned follow-up discussed while designing and implementing the quest system. This file deliberately separates **confirmed requirements**, **known deficiencies**, and **future features** so a suggested improvement is not mistaken for already-written code.
>
> **Scope:** The quest-mod development discussion and relevant earlier design decisions. The author has not inspected the local repository; code status below reflects implementations supplied and reviewed in chat, **not a fresh repository audit**. The user confirmed **successful builds through Milestone 2.7.7** and reports commits through 2.7.7. In-game tests have verified XP/item delivery, duplicate-claim rejection, full-inventory refusal and retry, and quest progress persistence across world restarts. The reward-inventory world-restart test is **not yet done**. This is a reported milestone status, not an independent audit of the local repository.

## How to maintain this guide

- `[ ]` Open/not yet verified; `[~]` partly implemented or only partially verified; `[x]` confirmed decision or completed milestone. Code and test status are based on the user’s reports unless explicitly noted.
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

## 2. Implemented milestones and verification (reported status)

**Verification labels:** **Built** = Gradle compilation succeeded; **In-game verified** = user tested behavior in a Minecraft world; **Pending runtime test** = code exists but is not yet proven in a restart/integration scenario. These are not automated tests or a repository audit.

| Milestone | Implemented | Verification |
|---|---|---|
| **2.1** Core quest model | `QuestState`, `QuestObjective`, `QuestDefinition`, `QuestProgress`; mandatory/optional progress, validation, explicit completion | Built |
| **2.2** Per-player progression | `PlayerQuestProgress`; UUID ownership, one active quest, history, lookup, progress and advancement methods | Built |
| **2.3** Campaign manager | `QuestManager`; registration, in-memory player progress, campaign order, quest activation and progression | Built; **progression hardening still pending** |
| **2.4.1** Reward types | `RewardDefinition` sealed interface; XP/item reward records | Built |
| **2.4.2** Reward inventory | `RewardClaimState`, `RewardEntry`, `PlayerRewardInventory`; per-quest groups, unlock, lookup, snapshots | Built |
| **2.4.3** Claim contract | `RewardClaimResult`, `RewardDelivery`, package-private `claimReward()` | Built; later integration tested in 2.6 |
| **2.4.4** Reward manager | In-memory `RewardManager`, UUID inventory map, unlock/claim/lookup, `computeIfAbsent()` | Built; **not persistent** |
| **2.5.1** Minecraft XP delivery | `MinecraftRewardDelivery`, `ServerPlayer.giveExperiencePoints()` | Built; **100 XP granted in-game** |
| **2.5.2** Minecraft item delivery | Registry lookup, `ItemStack` planning, whole-reward inventory preflight, commit only if all items fit | Built; **5 iron delivered, full inventory rejected with no partial grant**, retry succeeded |
| **2.5.3** Delivery test command | `/tamtest xp`, `/tamtest item` via Brigadier and NeoForge | **In-game verified** |
| **2.6** Claim integration test | `/tamrewardtest unlock/status/claim`; calls `RewardManager`; duplicate-claim prevention and independent bonus XP | **All user-performed in-game tests passed**; temporary in-memory test manager |
| **2.7.1** SavedData skeleton | `CampaignSavedData`, NBT test counter, versioned save format, `setDirty()` | Built |
| **2.7.2** SavedData counter test command | `/tamsavetest increment/value` | Code supplied; a distinct counter-specific restart result was **not explicitly reported** |
| **2.7.3** Quest progress codec | `PlayerQuestProgressCodec`, restore factories, NBT validation and null-safety checks | Built |
| **2.7.4** Quest world save | `CampaignSavedData` v2, UUID-indexed quest storage, quest codec integration | Built as part of subsequent verified test |
| **2.7.5** Quest persistence test | `/tamquestsave` isolated debug quest, main/optional objective progress, completion and restart | **All in-game tests passed across world restarts** |
| **2.7.6** Reward inventory codec | `PlayerRewardInventoryCodec`, restored claim states, serialized XP/item entitlements and group order | **Built and committed** |
| **2.7.7** Reward inventory world save | `CampaignSavedData` v3, `RewardInventories`, v1/v2 read compatibility, get/store methods | **Built and committed**; **reward restart test pending** |

**Current save schema:** `FormatVersion = 3`; keys: `TestValue`, `Players` (quest state), and `RewardInventories` (earned rewards and claim states). The two player-data maps reside in `CampaignSavedData`, but the active quest/reward managers still keep independent in-memory maps. This source-of-truth split is the immediate integration risk (P-001).

**Source-of-truth warning:** The public API currently exposes mutable `QuestProgress`/`PlayerQuestProgress` and `PlayerRewardInventory` references. Storing a reference and calling `setDirty()` once does not automatically mark later mutations dirty. These issues are *not resolved* by codecs compiling.

**Local edit checkpoints:** The user chose an `int`-only remaining-capacity calculation, a traditional loop for `canAdvance()`, and nonblank `QuestDefinition` description validation. Compilation has succeeded, but the assistant has not inspected those exact local source files; verify before closing C-001/C-002.

---

## 3. P0 — Correct before wiring up the Advance Quest button

### Q-001 — One authoritative quest advancement operation

**Status:** `[ ]` **P0** · Affected: `QuestManager`, `PlayerQuestProgress`, `QuestProgress`, future reward manager.

**Problem:** Current methods `completeActiveQuest()` and `activateNextQuest()` can be called separately. `completeActiveQuest()` finalizes the quest **without invoking `PlayerRewardInventory.unlockRewards()`**. Interruption between the actions could permanently lose earned rewards or leave progression inconsistent.

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

**Status:** `[~]` **P0 — ordinary full-stack behavior in-game verified; crash/replay durability still open** · Affected: reward entitlement model, claim workflow, inventory/XP delivery, persistence.

**Problem:** The reward UI offers independently claimable entries but **never** partial claims. Inventory overflow and duplicated click/network events must not lead to partial or repeated grants. `MinecraftRewardDelivery` now performs an inventory-copy preflight and applies changes only when all items fit; full inventory rejection and retry have passed in-game testing. However, `claimReward()` still trusts the delivery callback contract, and the active `RewardManager` does not yet persist claim changes automatically. A crash between granting items/XP and saving claim state can still permit duplication on restart.

**Required correction:** Assign stable entitlement IDs unique to player + quest + reward entry. Validate before delivery, ensure inventory can accept the entire item count (including legal stack splitting across slots if needed), and update claim state exactly once in a controlled server operation. The delivery implementation must not return `false` **after** granting a partial quantity. Handle XP as an indivisible entry. Enforce claim execution on the authoritative server thread; test double-clicks/replayed packets and inventory changes after capacity preflight. Design crash/recovery consistency for inventory/XP versus saved claim state; Minecraft inventory and mod saved data may have different persistence timing, so do **not** assume a database-style transaction exists.

**Acceptance:** Claiming `5 cogwheels` grants exactly five or zero; claiming `100 XP` grants exactly 100 or zero; replay never awards more than once.

### Q-007 — Per-player persistent save/load and consistency

**Status:** `[~]` **P0 — schema implemented, quest restart tested, manager integration and reward restart test pending** · Affected: `CampaignSavedData`, NeoForge lifecycle, both managers, player login/logout, reward inventory.

**Problem:** `CampaignSavedData` now serializes both quest and reward state (schema v3), and quest progress was verified across restarts through a debug command. **But** `QuestManager` and `RewardManager` still own separate in-memory maps; their mutations are not automatically persisted. There is no confirmed reward-claim restart test.

**Required correction:** Implement world/server-owned persistent player progression and reward entitlements, keyed by UUID; handle state restoration, dirty marking, restarts, and player rejoining. Validate loaded values: active ID must exist and map to exactly one `ACTIVE` quest; no completed quest can become active; progress values must be within each objective's allowed range; claimed rewards remain claimed. Plan schema/version migration for future JSON or save format updates. Ensure world-specific isolation and lifecycle cleanup.

**Acceptance:** Restarting the world/server preserves player objectives, current quest, completed quests, earned/unclaimed/claimed rewards, without granting duplicate progress or corrupting states.

---

## 4. P1 — Reward system and UI

### R-001 — Reward definitions and entitlement inventory

**Status:** `[~]` **P1 — domain, delivery and NBT codecs implemented; real quest entitlement generation pending** · Files: `reward/*`, `integration/MinecraftRewardDelivery.java`, `persistence/PlayerRewardInventoryCodec.java` and `CampaignSavedData.java`. (`[~]` means started, not closed.)

**Implemented:** Sealed XP/item hierarchy, validated immutable definitions, per-player quest groups, available/claimed state, duplicate ID checks, claiming, in-game XP/item delivery, full-inventory rejection, NBT encoding/decoding and schema-v3 world storage. Build success and the ordinary claim workflow have been confirmed in-game; restart persistence of reward state has **not** been verified.

**Remaining:** Associate rewards with quest definitions and main/optional sources; freeze entitlements on authoritative quest advancement; make the live `RewardManager` use the persistent store; mark every successful claim/unlock dirty; verify restart behavior and crash durability; preserve globally unambiguous entitlement identity (player + quest + reward ID).

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


### R-006 — Separate reward claiming from Minecraft delivery, and enforce complete delivery

**Status:** `[~]` **P0 — ordinary Minecraft XP/item delivery tested, advanced safety open** · Affected: `RewardDelivery`, `PlayerRewardInventory.claimReward()`, `MinecraftRewardDelivery`, live manager and persistence.

**Current model:** `RewardDelivery.deliver()` returns a `boolean`. The Minecraft implementation preflights available inventory space and has passed manual full-inventory failure/retry tests. `claimReward()` updates claim state only on `true`; this is not a crash-safe guarantee and cannot defend against arbitrary callbacks with partial side effects.

**Implemented:** In-game delivery of 100 XP and 5 iron ingots, entire-item-capacity preflight, complete rejection when inventory is full, and successful retry after freeing a slot. `RewardClaimTestCommand` also verified duplicate-claim rejection and independent optional XP claiming.

**Remaining:** Keep the path bound to an authenticated server player; ensure no mutation between inventory preflight and apply; test quantities spanning multiple stacks and non-64 stack limits; inject failing delivery callbacks; use an actionable result for inventory-full failures; address crash consistency of XP/inventory and claim-state persistence.

**Acceptance:** A claim of `5` items either grants exactly `5` and marks claimed, or grants `0` and remains available. `100 XP` behaves equivalently. Automated failure injection tests cover delivery rejection.

### R-007 — Stable reward identity, metadata, and optional-source accounting

**Status:** `[ ]` **P1** · Affected: `RewardEntry`, `PlayerRewardInventory`, future quest JSON/schema.

**Current model:** `RewardEntry` stores only `(questId, reward, state)`; there is no explicit origin type (`MAIN` vs `OPTIONAL`) or optional objective ID. Duplicate IDs are checked **within a quest**; this is sufficient only if claim requests always include quest ID and reward ID, bound to an authenticated player.

**Required correction:** Define stable per-quest reward IDs, a canonical entitlement key, source metadata and optional-objective association. Ensure completed-quest rewards cannot be regenerated with different payloads on retry or save recovery. Decide whether a zero-reward quest should still record a finalized entitlement group: current `unlockRewards(questId, List.of())` records an empty group and returns success.

**Acceptance:** Two XP rewards in the same quest remain distinct; one optional reward is unlocked only when its associated objective is complete; client labels are truthful without revealing locked reward data.

### R-008 — Immutable claim-state replacement and storage consistency

**Status:** `[~]` **P1 — immutable replacement and codec built; live synchronization unverified** · Affected: `PlayerRewardInventory`, `RewardManager`, `CampaignSavedData`.

**Current model:** `RewardEntry` is an immutable record. On successful delivery, `claimReward()` creates a new list, replaces the entry, and installs `List.copyOf(updatedEntries)` into the `LinkedHashMap`.

**Implemented:** Immutable entry replacement, insertion-ordered per-quest groups, `PlayerRewardInventoryCodec` serialization of reward definitions and claim state, and version-3 SavedData storage.

**Remaining:** Route all mutations through trusted server ownership; mark every successful unlock/claim dirty via persistent manager integration; test preserved order and claimed/unclaimed status after restart. `Collections.unmodifiableMap(new LinkedHashMap<>(...))` is safe against external mutations only while its lists/entries remain immutable.

**Acceptance:** Claiming one entry doesn't alter other entries, quest-group ordering, or old snapshots; restart restores each claimed/unclaimed state correctly.

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


### C-007 — Package-private access is encapsulation, not a security boundary

**Status:** `[ ]` **P1** · Affected: `PlayerRewardInventory.claimReward()`, future `RewardManager`.

**Discussion:** The method was deliberately declared without `public`, giving Java package-private access. This prevents unrelated packages from calling it directly, but another class in the same package can still call it, and it does not authenticate a player or validate network traffic.

**Required:** Centralize calling paths through a server-owned manager; authenticate the requesting `ServerPlayer`, validate entitlement ownership and claim eligibility, and never expose mutation APIs in client payloads. Treat package visibility as API organization only.

### C-008 — Sealed reward hierarchy extensibility decision

**Status:** `[ ]` **P2** · Affected: `RewardDefinition`.

**Discussion:** Current `sealed interface RewardDefinition permits ExperienceReward, ItemReward` is appropriate for known built-in reward types, and both permitted records are implicitly final. If the mod later supports externally registered reward types from third-party addons, the sealed hierarchy will require redesign or an intentional extension point.

**Required:** Retain the sealed hierarchy while rewards are Create-focused and closed; revisit only if a real extensibility requirement emerges. Avoid introducing generic nullable reward fields just to support hypothetical types.

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
- Claim full entry or none; cannot claim a fraction; `RewardDelivery` failure leaves entitlement unclaimed and grants zero items/XP; inventory full leaves it available.
- Repeated `unlockRewards()` cannot generate duplicate entitlements; duplicate claim requests/restarts cannot duplicate XP or items; saved state restores correctly.
- Multiple rewards of the same type (100 XP and 50 XP) claim independently; group ordering remains stable after claiming.
- `RewardClaimResult` reports `NOT_FOUND`, `ALREADY_CLAIMED`, `DELIVERY_FAILED`, and `SUCCESS` correctly; test `RewardDelivery` callbacks that throw/fail/partially mutate and define the failure contract.
- An empty reward list, duplicate reward IDs, and invalid reward definitions produce specified behaviors.
- Client synchronization does not expose future quest definitions or unrevealed reward metadata.

**Verification:** User-reported **manual in-game tests passed** for XP/item delivery, duplicate claim rejection, full-inventory refusal and retry, and quest progression persistence across world restarts. **Automated unit/GameTests, multiplayer tests, persisted reward-claim restart tests, and crash/replay tests remain unperformed or unreported.** Maintain automated coverage alongside new integration work.

### I-005 — Logging, diagnostics, and schema upgrades

**Status:** `[ ]` **P2**.

**Required:** Add clear diagnostics for invalid definitions, missing quest IDs, failed advancement, rejected claims, persistence failures, and incompatible save/schema versions. Define a strategy for removed/renamed quest IDs without silently corrupting player history.

### I-006 — Documentation for a public GitHub portfolio

**Status:** `[ ]` **P2**.

**Required:** Maintain a clear README, setup/build instructions for Java 21 / Minecraft 1.21.1 / NeoForge, optional test profile notes, architecture diagram, quest/reward JSON format, development roadmap, and human-readable changelog. Clearly distinguish working features from proposed features in public documentation.

---

## 8. New persistence and integration corrections (Milestones 2.5–2.7)

### P-001 — Eliminate duplicate authoritative manager state

**Status:** `[ ]` **P0** · `QuestManager.playerProgress` and `RewardManager.playerInventories` are still separate in-memory maps from `CampaignSavedData.playerQuests` and `CampaignSavedData.playerRewards`.

**Required:** Make server/world-owned persistent data the single source of truth. Refactor manager constructors or operations to use the current `CampaignSavedData`, without parallel authoritative maps. Scope manager lifetime to the correct server/world. Never use the temporary static debug `RewardManager` as production state.

**Acceptance:** Changing a quest or reward through the real manager modifies exactly the saved state that is subsequently reloaded; no stale duplicate copies or cross-world leakage.

### P-002 — Make dirty marking inseparable from trusted mutations

**Status:** `[ ]` **P0** · A mutable value returned from `CampaignSavedData.getPlayerQuestProgress()` / `getPlayerRewardInventory()` may be changed without a `store...()` call. The initial store marks data dirty only at that moment.

**Required:** Restrict mutable reference exposure and perform updates through server-owned persistence-coordinated APIs (or a carefully encapsulated mutation callback). On every successful quest or reward transition, mark the correct `SavedData` dirty. Document behavior when validation fails (no dirty flag or change).

**Acceptance:** Every persisted mutation survives normal restart; mutation cannot be silently lost because a caller forgot to call `store...()`.

### P-003 — Validate and migrate loaded world data safely

**Status:** `[~]` **P1** · Schema versions 1/2/3 exist and the codecs reject several invalid saved values. Full schema/data migration and recovery are pending.

**Required:** Test loading v1 counter-only, v2 quest-only, and v3 quest+reward saves. Reject wrong NBT field types explicitly where needed (do not silently treat required missing/wrongly typed integers as zero); verify quest/reward IDs and stored bounds against definitions. Preserve earlier reward entitlement amounts when quest definitions change. Decide how to recover from corrupt or future-version saves; back up before any migration.

**Acceptance:** Compatible old saves retain valid progress; invalid saves fail clearly or recover according to a documented policy, never silently duplicate or lose rewards.

### P-004 — Verify reward claims persist across world restarts

**Status:** `[ ]` **P0/P1 — next test milestone 2.7.8** · Reward codec and schema-v3 world storage compile, but the temporary `/tamrewardtest` uses a separate in-memory `RewardManager` reset on server stop.

**Required:** Create an isolated saved-data-backed debug reward test (or refactor the test manager to use `CampaignSavedData`) that unlocks 100 XP / 5 items / 50 XP, claims one, restarts the same world, confirms the exact remaining/claimed statuses, rejects re-claim of a previously claimed entry, then claims a remaining entitlement. Verify group ordering and player UUID isolation.

**Acceptance:** Claim and unlock states persist through restart without repeat grants; each reward remains indivisible.

### P-005 — Handle crash timing and exactly-once delivery

**Status:** `[ ]` **P0** · Minecraft inventory/XP persistence and the mod's `SavedData` do not automatically commit atomically.

**Required:** Choose a recoverable claim protocol and test interrupted flows (including the window after item/XP delivery but before saving the claim marker, and the reverse). Avoid promising universal exactly-once semantics without a concrete recovery mechanism. Consider a pending/committed claim state, replay handling and server-owned journal only after evaluating Minecraft's persistence behavior.

**Acceptance:** A crash/reconnect/retry cannot silently multiply earned reward quantities or permanently erase an entitlement under the supported recovery policy.

### P-006 — Remove or gate administrator debug endpoints for release

**Status:** `[ ]` **P1** · Debug commands currently include `/tamtest`, `/tamrewardtest`, `/tamsavetest`, `/tamquestsave`; they are limited by level-2 permissions but can bypass actual quest earning.

**Required:** Keep dev-only commands disabled or gated behind a specific debug flag in release builds, or remove them. Ensure no arbitrary unlock or grant endpoint is available to ordinary players. Keep their tests reproducible through automated tests or dedicated dev configurations.

**Acceptance:** Normal gameplay cannot mint rewards or progression using debug commands; developer workflows remain documented and repeatable.

### P-007 — Ensure client confidentiality matches distribution model

**Status:** `[ ]` **P1** · No restricted gameplay network payloads or journal UI implemented yet.

**Required:** Send only permitted current-quest and earned-reward views. **Caveat:** hiding data from network messages does not make quest content unextractable if the complete quest JSON is bundled inside a client-installed mod JAR. Decide whether the secrecy goal means UI/network secrecy (achievable) or resistance to client file inspection (requires a different distribution model, e.g. server-exclusive content).

**Acceptance:** No unintended spoilers in gameplay/UI/network state; the limits of client-distributed assets are documented accurately.

### P-008 — Stable history ordering and restored identifiers

**Status:** `[ ]` **P2** · `PlayerQuestProgressCodec` reads quest entries into a `HashMap`, which does not preserve completion order. The reward inventory uses `LinkedHashMap` and retains insertion order.

**Required:** Decide whether completed quest history must display chronological/campaign order; if so, store sequence or explicit timestamps/order rather than relying on `HashMap` iteration. Preserve stable objective/reward IDs across updates and verify `QuestState.valueOf()`/`RewardClaimState.valueOf()` failure modes.

**Acceptance:** History/reward UI has deterministic ordering after save/load and content updates.

---

## 9. Prioritized implementation sequence

1. **Immediate:** Milestone 2.7.8: verify world-restored unlocked and claimed reward states, using `CampaignSavedData` rather than the temporary in-memory test manager (P-004). Do not claim durable behavior before this test passes.
2. **After the restart test:** Make `CampaignSavedData` the authoritative storage behind `RewardManager` and `QuestManager` (P-001, P-002); ensure every successful mutation marks SavedData dirty. Preserve player/world isolation.
3. **Before exposing real claim/advance requests:** Address crash/replay claim-safety strategy (P-005, Q-006), server identity checks (Q-005), and controlled mutation API (Q-004).
4. **Build real campaign logic:** Couple main/optional objective outcomes to stable reward definitions, derive the next quest server-side, atomically finalize a quest with earned entitlements, and sync only permitted views (Q-001–Q-003; R-002, R-007).
5. **Create content and UI:** JSON quest schema, explicit order, gameplay objective detection, Create API hooks, spoiler-safe journal/hints/ingredient lists, reward claim UI and synchronization.
6. **Before public release:** Automated tests, multiplayer/restart/crash compatibility tests, backup/migration tests, debug command release gating and public documentation.

> **Do not wire the current separate `QuestManager.completeActiveQuest()` method directly to a player's Advance action.** Persistence of individual objects is not yet a coordinated advancement transaction.

---

## 10. Open design decisions (not yet finalized)

These are **questions to resolve during implementation**, not prior commitments:

- **Advancement durability:** exact strategy for handling partial writes and crash recovery across player quest state, reward entitlements, and Minecraft inventory/XP.
- **Reward claim delivery:** how to preflight inventory insertion and protect against races or unexpected slot changes; where XP grant and claimed-state persistence are ordered; how to recover from process termination between delivery and save.
- **Reward `deliver()` failure contract:** whether to keep `boolean` or use a structured result (inventory full, unknown item, other failure), and how to guarantee no side effects when the callback reports failure.
- **Reward source metadata:** final schema for identifying main vs optional objective rewards, and whether zero-reward quests create an empty reward group.
- **Quest definition updates:** how to migrate old saved objective IDs/progress after JSON data pack updates, and whether definitions can reload live.
- **Create objective verification:** how to prove a machine is functional rather than merely built/placed, without forbidding creative alternate solutions.
- **Rewards/UI presentation:** whether newly unlocked rewards should produce a notification in addition to appearing in the grouped Rewards UI.
- **Progress data exposure:** exact read-only API/DTO shape versus package-private domain methods; choose the simplest safe design.
- **Failure feedback:** whether to use result enums or richer error objects once the UI needs actionable messages.

---

## 11. Revision log

| Date | Change |
|---|---|
| 2026-10-25 | Initial consolidation of decisions, deferred corrections, limitations, and planned features from the quest-mod discussion. |
| 2026-10-26 | Confirmed initial reward model through 2.4.3; added R-006–R-008 and C-007–C-008. |
| 2026-10-26 | Updated to 2.7.7. Recorded compiled/committed reward persistence v3, in-game XP/item/claim/full-inventory tests, verified quest persistence across restarts, and remaining reward-restart test; updated Q-006/Q-007/R-001/R-006/R-008/I-004, added P-001–P-008, refreshed roadmap and verification boundaries. |

**Maintenance rule:** Whenever we say "we'll fix that later", add or update an item here **in the same development milestone**. Never close an item solely because the build succeeds; verify its acceptance criteria.
