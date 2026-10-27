# The Age of Machinery

*A spoiler-conscious, Create-focused quest and progression mod for Minecraft Java Edition 1.21.1 (NeoForge).*

> **Development checkpoint — 27 October 2026 · Milestone 2.14 planned; 2.13.6 last completed.** The developer reports successful builds and commits through **2.13.6**. The mod now loads server-side quest definitions from Minecraft data packs, validates them, supports compatible presentation-only `/reload` changes, and uses real crafting and ground-item pickups to progress the prototype campaign. **Create-specific engineering objectives, the player-facing Quest Journal, networking and public release hardening are not implemented.** Create 6.0.10 (Minecraft 1.21.1/NeoForge) is the verified official integration target; Gradle dependency setup is the next task, **not yet a verified build**. A two-player test is still **pending / not performed**.
>
> **Observed follow-ups:** A test pack setting `requiredAmount` to `0` caused a game failure without a useful visible explanation; the exact exception and whether it occurred on startup or reload have not been established. In addition, each matching item pickup currently emits a quest-progress chat/system message. Both are tracked in [`docs/FUTURE_CORRECTIONS.md`](docs/FUTURE_CORRECTIONS.md) as **I-011** and **I-012**.

## Project vision and gameplay rules

The Age of Machinery is intended to guide players through a discovery-oriented Create/Create+ engineering campaign rather than generic grinding. The current quests are **vanilla Minecraft prototypes**, not the final engineering campaign.

- **Exactly one active quest per player.** Completed quests remain in history; future quests are not populated as active progress records.
- **Mandatory and optional objectives.** Mandatory progress unlocks the right to **manually advance**; optional tasks remain available until advancement and may grant extra rewards.
- **Spoiler-conscious progression.** Future quests and the current quest's rewards must not appear in ordinary player UI. Only earned rewards become discoverable after quest finalization. Operator diagnostic commands are not player-visible policy.
- **Independent claims.** Earned XP/item rewards are claimable individually, without partial item delivery; unclaimed rewards survive quest transitions and ordinary world restarts.
- **Ingredient checklists.** First-time crafting/building tasks should show required ingredient quantities. JSON stores this metadata; a player-facing checklist is not built yet.
- **Server-authoritative progress.** Minecraft server events drive supported objective types. The client must never submit arbitrary progress amounts or another player's UUID.
- **Three progressively more specific hints** are planned; the hint system does not yet exist.

## Current functionality

| Subsystem | Implementation at 2.13.6 |
|---|---|
| Quest/reward models, active quest and optional tasks | Implemented; quest advancement is manual |
| Per-player state and history | `CampaignSavedData` NBT `FormatVersion = 3`, UUID-owned, detached snapshots and revision checks |
| Coordinated advancement/reward claims | Staged state commit, earned optional rewards frozen at advancement, individual claims and guarded delivery |
| Minecraft delivery | XP/item rewards supported; full-inventory item claim refusal and retry previously tested |
| Server runtime | One `CampaignRuntime` per Minecraft server, automatic new-player initialization, `ServerPlayer` identity checks and server-thread checks |
| Gameplay detection | `PlayerEvent.ItemCraftedEvent` and `ItemEntityPickupEvent.Post` route active-quest item objectives |
| Objective matching | `MANUAL`, `CRAFT_ITEM`, `PICKUP_ITEM` with registry item IDs through `CampaignObjectiveRouter` |
| JSON quest definitions | **Implemented:** schema, strict parser, model conversion, ingredient entries, campaign manifest and whole-campaign assembly |
| Data-pack activation | **Implemented:** active campaign loaded from server data packs on server startup; Java-built-in content is no longer the active source |
| Live `/reload` | **Implemented for compatible text-only changes**; structural changes rejected during the current server session |
| World-restart structural migration | **Not implemented:** existing saves do not have a verified persisted campaign-definition lock/migration policy |
| Error diagnostics for invalid packs | **Needs improvement:** `requiredAmount: 0` caused a failure without a useful visible explanation (I-011) |
| Quest progress notification | Temporary per-event chat/system messages; **must be replaced/gated** when journal UI is available (I-012) |
| Create Maven/runtime dependency | **Next — 2.14.1:** official documented target 6.0.10, Maven 6.0.10-280; actual dev build and Create+ profile compatibility not yet verified |
| Create machinery / automation events | Not implemented; planned for 2.14.2–2.14.7 |
| Client protocol, journal, hint/ingredient/reward UI | Not implemented; planned for 2.15 |
| Two-player simultaneous multiplayer verification | **Pending — cannot currently be tested with two accounts** |
| Crash-atomic reward delivery and automated regression coverage | Not established; release-hardening issues remain open |

**Verification scope:** The developer reports building and committing milestones through 2.13.6. Screenshots directly demonstrated an **applied cosmetic reload** and a **rejected incompatible quantity change** while preserving the active requirement. A malformed `requiredAmount: 0` pack was reported to cause a game failure without useful diagnostic feedback. A complete, independently inspected log of all 2.13.6 test scenarios (including the third-quest pack and optional rewards) has not been provided; successful compilation alone does not establish those individual results. The exact exception behind the invalid-pack failure is unknown.

## Current JSON-defined prototype campaign

The default server data pack defines an ordered two-quest campaign. Stable IDs must not be casually changed, because progress and reward claims reference them in saved state.

| Quest | Mandatory objective | Optional objective | Main rewards | Bonus if optional completed *before advancement* |
|---|---|---|---|---|
| **The First Workshop** — `theageofmachinery:prototype_first_workshop` | Pick up 16 Cobblestone | Pick up 8 Oak Logs | 25 XP and 2 Iron Ingots | 10 XP |
| **The First Tools** — `theageofmachinery:prototype_first_tools` | Craft 1 Crafting Table | Craft 1 Furnace | 50 XP | 15 XP |

The objective records include structured ingredient metadata, such as **4 planks** for a Crafting Table and **8 Cobblestone** for a Furnace. The ingredient data is authored configuration and does not itself implement a journal checklist or validate the actual crafting recipe.

*Acquisition* currently means a **successful ground-item pickup**; it does not include every inventory insertion path, nor prove the player mined the item. The listener counts items actually transferred (original stack minus remaining stack). Shift-click/bulk crafting counts, partial pickups and repeated drop/re-pickup are still regression/design topics.

A **third quest**, `theageofmachinery:prototype_storage_basics`, is provided as a **2.13.6 external data-pack test** to demonstrate adding a Chest-crafting quest without editing Java. It is **not part of the default two-quest manifest**. The existing debug claim command still only exposes `first` and `second` aliases.

## JSON/data-pack layout

```text
src/main/resources/data/theageofmachinery/
├── campaigns/
│   └── main.json                  # explicit, stable quest order
└── quests/
    ├── prototype_first_workshop.json
    └── prototype_first_tools.json

docs/
├── QUEST_JSON_FORMAT_V1.md
├── schemas/
│   ├── campaign.schema.json
│   └── quest.schema.json
├── milestones/                  # implementation notes for 2.13.x
└── tests/                       # development-only data-pack fixtures
```

The manifest (`formatVersion: 1`) lists ordered quest resource IDs. Each quest JSON holds stable IDs, objectives, trigger types/targets, ingredient checklists, main rewards and optional rewards. The parser checks fields and model constraints; the assembler checks referenced quests, ordering and registered item IDs. Live loading also checks ingredient tags against the active resource/tag state.

**Runtime policy:** On server startup the active data packs are loaded and validated to produce a campaign, then registered once. During `/reload`, a replacement is accepted only if compatible with the running campaign: **titles and descriptions may change**; IDs, order, quantities, triggers, ingredient lists and rewards may **not**. A rejected replacement leaves the previous in-memory campaign active. A successful Minecraft `/reload` message does not necessarily mean our mod accepted the new campaign; inspect `/tamruntime reloadstatus`.

**Known incompatibility:** Structural changes can still become active on a later **world/server restart** because there is no persisted campaign-version lock. Treat live-reload safety and cross-restart migration as separate issues. Use disposable worlds to test different campaign structures; back up valuable saves.

**Invalid-data behavior requires improvement:** A pack with `"requiredAmount": 0` was observed to cause a game failure with insufficient visible explanation. The correct policy is to reject invalid quest content, **not** silently accept or substitute a fallback; however, validation errors must clearly identify the affected resource, field path, offending value and cause, and startup/reload handling must be documented and tested. See I-011.

## Architecture

```text
MinecraftServer
  └── active data packs / resource manager
        ├── CampaignDataPackLoader
        ├── CampaignJsonParser + CampaignManifest
        ├── CampaignDefinitionAssembler
        └── CampaignDefinition (immutable, ordered)
              └── CampaignRuntime (one per server session)
                    ├── CampaignAdvancementService + registered QuestManager
                    ├── RewardManager
                    └── CampaignSavedData (UUID-keyed authoritative NBT v3)

CampaignPlayerEvents            first-login initialization; preserve returning saves
CampaignCraftingEvents          server crafting event adapter
CampaignItemPickupEvents        successful ground-pickup event adapter
  └── CampaignObjectiveRouter   match only active-quest trigger/item; update runtime
CampaignReloadEvents            after data-pack reload, candidate + compatibility check
CampaignReloadCompatibility     text-only live replacement policy
MinecraftRewardDelivery         actual server-side XP/item reward delivery
command/*                       operator-only development testing/diagnostics
```

A compatible runtime reload replaces campaign definitions and the registered advancement service **without creating or resetting the player save store**. This is an in-session guarantee; it is not a persistent migration system.

**Reward durability caveat:** Minecraft XP/inventory and mod `SavedData` are not a single crash-atomic transaction. An unexpected crash after item/XP delivery but before durable claim state may produce an uncertain outcome. Exactly-once delivery is **not** guaranteed.

## Development requirements

- Minecraft Java Edition **1.21.1**
- NeoForge **21.1.233** (project development target)
- **JDK 21**, Gradle/ModDevGradle, Windows, VS Code
- Mod ID: `theageofmachinery`
- Current Java package: `com.TheAgeOfMachinery` (case-sensitive)
- Create: **official Minecraft 1.21.1 target is 6.0.10**; official addon Maven coordinates use `create_version=6.0.10-280`, Ponder `1.0.82`, Flywheel `1.0.6`, Registrate `MC1.21-1.3.0+67`. Adding the dependency to this project and actual Create+ modpack verification are **pending 2.14.1**.
- Official dependency guide: https://wiki.createmod.net/developers/depend-on-create/neoforge-1.21.1

From repository root:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

The Gradle dev client is separate from a Modrinth Create+ profile. Use test worlds and backups when working with incompatible packs or save formats.

## Operator-only development commands

These commands are for testing and diagnosis, **not** the finished player experience. They must be restricted or removed before a public release. Some command names may depend on which test harnesses remain registered in a branch.

| Command | Purpose |
|---|---|
| `/tamruntime status` / `campaign` / `player` | Inspect session, ordered quest IDs and active objectives |
| `/tamruntime progress <objectiveId> <amount>` | **Debug-only** simulated objective progress |
| `/tamruntime advance` | Finalize eligible active quest (manual advancement) |
| `/tamruntime rewards` | Inspect *unlocked* reward entries and claim states |
| `/tamruntime claim first <rewardId>` / `second <rewardId>` | Claim earned prototype rewards; aliases currently limited to two quests |
| `/tamruntime verify` / `audit` | Check rejected operations and read-only player diagnostics |
| `/tamjson test` | JSON parser and negative-case tests |
| `/tamregistry test` | Whole-campaign validation tests |
| `/tamreloadtest` | Compatible/incompatible definition policy test; **spelling is reload** |
| `/tamruntime reloadstatus` | Whether mod campaign reload was applied or rejected |
| `/datapack list enabled` | Inspect Minecraft-enabled packs (vanilla command) |

Additional test harness commands exist for persistence, staged advancement and reward failures. Operator commands that display full campaign IDs are spoiler-sensitive and must not be accessible to ordinary players.

**Temporary progress feedback:** The active item-event router sends quest-update **chat/system messages on each matching pickup or craft event**. This is useful without a UI but will flood the chat during ordinary gameplay. The intended UI will receive relevant progress state/deltas and display restrained updates (e.g., completion notification or journal update) instead of one chat line per item. Production `LOGGER` errors/warnings for administrators are a separate channel and should not be disabled with chat feedback.

## Create integration architecture and milestones (2.14)

**Design principle:** count *verified engineering behavior*, not merely an item existing in inventory or a machine block being placed. Reuse `CampaignRuntime`, server authority, `CampaignSavedData`, JSON quest models and the existing event router. Do **not** create another progression engine.

**Dependency choice:** Create is intended as a **required** dependency for the released addon (both client and server). As of 27 October 2026, the official 1.21.1 NeoForge addon guide targets **Create 6.0.10** and the development Maven build `6.0.10-280` with **Ponder 1.0.82**, **Flywheel 1.0.6** and **Registrate MC1.21-1.3.0+67**. The reference **Create+ modpack release is 6.0.0-alpha-f**. The user confirmed their built mod is discovered when launching the modpack; the precise installed Create/loader versions and full in-world objective regressions have not been independently documented. Source: <https://wiki.createmod.net/developers/depend-on-create/neoforge-1.21.1>.

| Milestone | Goal | Acceptance / deliverable | Status |
|---|---|---|---|
| **2.14.1** | Create dependency and dev environment | Gradle dependencies/build and dev client succeeded; mod detected in Create+ 6.0.0-alpha-f | **Build and modpack load user-confirmed; in-world quest/mechanical regression not separately evidenced** |
| **2.14.2** | Machine-event contracts, attribution and JSON semantics | Define authoritative evidence of operation, player ownership, offline/multiplayer behavior and idempotency | Planned |
| **2.14.3** | Kinetic power objective | Recognize a functioning rotational network with meaningful speed/stress/connection conditions, not block placement | Planned |
| **2.14.4** | Mechanical Press processing | Recognize a completed pressing operation with credible recipe/output provenance; reject mere item possession | Planned |
| **2.14.5** | Generic processing and automated production | Reusable objectives for selected Millstone, Crushing Wheels and Mixer workflows; batch counting and duplicate prevention | Planned |
| **2.14.6** | Create progression chapter | Data-pack-authored Create quests, structured ingredients, optional engineering challenges, manual advancement and earned rewards | Planned |
| **2.14.7** | Real-mod gameplay regression | Powered/unpowered cases, processing correctness, persistence, optional outcomes, repeated operations and actual Create+ compatibility; document untested multi-account scenarios | Planned |

### Ownership policy agreed for 2.14.2 (I-013)

**Initial implementation: builder = owner.** When the server can reliably attribute construction/placement of a supported Create machine, associate that installation with the placing player's UUID. The association must be authoritative and persisted; never infer ownership from the closest player, the current item holder or the last player to activate the machine. Exactly how a multi-block installation is identified, and which placement action defines its builder, remain implementation details to settle against Create APIs.

**Future feature: area-registration item for cooperative workshops (deferred; not part of the first machine integration).** The item establishes a bounded area/installation in which players can register for shared credit. Every registered player is an **owner**; any owner can open or close registration. When a qualifying machine event occurs in that area, each registered owner receives progress **independently, only if their currently active quest includes a matching objective**. Someone on another quest, with a different active objective, or with a completed campaign receives no progress from that event. Merely being physically nearby is not sufficient for membership or credit.

**Multiplayer-first data model:** Represent ownership internally as a set of player UUIDs, even while initial placement adds only one owner. Keep ownership separate from quest completion state; process each eligible owner independently through the existing server-authoritative campaign runtime. Do not introduce a shared global quest counter, and preserve per-player advancement and rewards. Ensure the same verified machine event cannot credit any one owner more than once.

**Still to design before implementation:** installation identity for connected/multi-block networks; registration area overlap/conflict and membership removal; persistence, teardown/replacement, permissions/owner revocation, unloaded chunks, offline members, and handling of placement by automation or unknown actors. For the initial version, events without reliable builder attribution must not be arbitrarily credited. Two-player end-to-end behavior remains **untested**.

**Provenance and duplicate counting are unresolved (I-014):** collecting an Iron Sheet is not proof that a Mechanical Press produced it. Observe actual successful operations where possible, choose a single authoritative count point, and avoid crediting the same output through overlapping pickup/processing handlers.

**Chat-output policy (I-012):** routine per-item quest chat is temporary debugging feedback, not the target UX. Create processing can generate high-frequency updates; the player-facing UI must aggregate progress rather than write a line per machine operation. Do not suppress actionable server errors (I-011).

**Known release risks remain open:** startup-invalid-JSON diagnostics (I-011), campaign definition locking across world restarts (P-003), crash-timing reward delivery (P-005), two-player isolation (I-003), debug command access (P-006) and automated regression coverage (I-004). Use disposable worlds for integration experiments.

## Roadmap

- [x] **2.1–2.6:** quest models, rewards and Minecraft delivery.
- [x] **2.7–2.10:** persistent save data, revision-controlled staging and guarded claims.
- [x] **2.11:** server-scoped runtime, quest registration, login lifecycle and server authority.
- [x] **2.12.1–2.12.4:** vanilla crafting/pickup tracking, objective routing and development gameplay verification.
- [x] **2.13.1:** versioned JSON schema, manifest and example quests.
- [x] **2.13.2:** strict parser and Java model conversion, structured ingredient metadata.
- [x] **2.13.3:** whole-campaign reference/registry validation and ordered assembly.
- [x] **2.13.4:** startup data-pack loading into the server runtime, including an override fixture.
- [x] **2.13.5:** compatible presentation-only live reload, incompatible structural reload rejection.
- [x] **2.13.6:** end-to-end JSON integration verification milestone; build and commit reported successful. **Known invalid-pack diagnostic failure remains open.**
- [x] **2.14.1 — Dependency setup:** successful Gradle build, client launch and mod discovery in Create+ 6.0.0-alpha-f reported; precise modpack-internal Create/loader versions and in-world regression evidence remain to be captured.
- [ ] **2.14.2–2.14.7 — Create achievements:** trusted machine events, attribution, kinetics, pressing, general processing, JSON campaign chapter and Create+ integration tests.
- [ ] **2.15 — Networking and Quest Journal:** spoiler-filtered client state, structured ingredient checklists, hints, manual advancement and reward claims; replace noisy per-item chat feedback.
- [ ] **Release hardening:** actionable invalid-pack errors (I-011), startup definition locking/migrations (P-003), full automated regression suite, security/permission checks, two-player verification (I-003), crash-recovery policy (P-005), debug-command gating.

**2.13 is complete as a development sequence, not a claim of production readiness.** Reported compilation/commits and selected in-game checks do not establish all malformed-data, multiplayer, automation or crash-timing behavior.

## License

**All Rights Reserved** under the currently recorded project configuration. A public GitHub repository alone does not grant rights to redistribute or modify the code; explicitly choose a license if those rights should be granted.
