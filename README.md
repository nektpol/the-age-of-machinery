# The Age of Machinery

> **2.14.6 system test passed — 28 October 2026:** the user confirmed Water Wheel progress, manual advancement and earned reward claims. Build, parser/domain regressions, schema/recipe checks and null analysis also passed. **All current campaign content is provisional.** The default cobblestone-before-crafting-table sequence has invalid prerequisites and is not an approved survival opening. Finish the mod systems first, then redesign progression from the beginning for the **full Create+ inventory and addons**. The [chapter ZIP](docs/datapacks/The_Age_of_Machinery_2.14.6_Create_Workshop.zip) is a campaign-system fixture, not a final campaign. **2.14.7 ownership checks passed** and the user approved moving on; this fixture's restart checks remain unconfirmed. Next: **2.15 — Client Synchronization and Visibility**. See [milestones.md](milestones.md) for the six implementation steps.

*A spoiler-conscious, Create-focused quest and progression mod for Minecraft Java Edition 1.21.1 (NeoForge).*

> **Development checkpoint — 27 October 2026 · 2.14.4 completed, tested, and committed (developer report).** The Age of Machinery now supports server-authoritative quest progression, JSON/data-pack campaigns, builder-attributed Create machine ownership, live kinetic operation detection (2.14.3), and observed Mechanical Press item processing (2.14.4). The developer reports a successful build, 100% success on the milestone checks, and a Git commit for 2.14.4. This document is a retrospective update from the 2.14.2 documentation snapshot and the 2.14.3–2.14.4 implementation discussed in development; it is **not a direct audit of the current local Git working tree or commit hash**. **Current checkpoint: 2.14.6 system test accepted; final campaign design deferred.**
>
> **Development launch:** the full Create+ 6.0.0-alpha-f pack is run through Gradle `runClient` in isolated `run-createplus`, with The Age of Machinery loaded from the development source set for IDE debugging. A previous launch blocker was resolved by correcting the local Java environment; exact changes from that external fix were not recorded here. Do not revert to a reduced Create setup or manual Modrinth JAR-copy workflow.
>
> **Open release risks:** two-player simultaneous testing remains **pending**; crash-atomic reward delivery, cross-restart campaign-definition migration, malformed-data error diagnostics (I-011), frequent quest chat notifications (I-012), and comprehensive automation/batch/idempotency coverage (I-014) are not production-verified. See [`docs/FUTURE_CORRECTIONS.md`](docs/FUTURE_CORRECTIONS.md).

## Project vision and gameplay rules

The Age of Machinery is intended to guide players through a discovery-oriented Create/Create+ engineering campaign rather than generic grinding. The bundled default remains the two-quest vanilla prototype. The installable **Create+ Workshop** pack adds six provisional quests after that prelude; its campaign-system smoke test passed. Neither the prelude nor this chapter is an approved final campaign.

- **Exactly one active quest per player.** Completed quests remain in history; future quests are not populated as active progress records.
- **Mandatory and optional objectives.** Mandatory progress unlocks the right to **manually advance**; optional tasks remain available until advancement and may grant extra rewards.
- **Spoiler-conscious progression.** Future quests and the current quest's rewards must not appear in ordinary player UI. Only earned rewards become discoverable after quest finalization. Operator diagnostic commands are not player-visible policy.
- **Independent claims.** Earned XP/item rewards are claimable individually, without partial item delivery; unclaimed rewards survive quest transitions and ordinary world restarts.
- **Ingredient checklists.** First-time crafting/building tasks should show required ingredient quantities. JSON stores this metadata; a player-facing checklist is not built yet.
- **Server-authoritative progress.** Minecraft server events drive supported objective types. The client must never submit arbitrary progress amounts or another player's UUID.
- **Three progressively more specific hints** are planned; the hint system does not yet exist.

## Current functionality

| Subsystem | Status through 2.14.4 |
|---|---|
| Quest and reward domain | Implemented: one active quest per player, mandatory/optional objectives, manual advancement, completed history and independently claimable rewards |
| Per-player state | `CampaignSavedData` NBT `FormatVersion = 3`; UUID ownership, detached snapshots and revision checks |
| Advancement/reward authority | Server-scoped `CampaignRuntime`, staged commits, earned optional rewards frozen on advancement, guarded claims and actual Minecraft XP/item delivery |
| Vanilla event objectives | `PICKUP_ITEM` and `CRAFT_ITEM` matched against the active quest by `CampaignObjectiveRouter`; ground-pickup semantics remain limited |
| JSON campaigns | Versioned schema, strict parsing, ordered manifest, registry validation, ingredient metadata and server data-pack loading |
| Hot reload | Compatible text/presentation-only `/reload` accepted; structural changes refused within a running server session; cross-restart definition lock remains open |
| Create dependencies | Create 6.0.10 target (`6.0.10-280` development artifact), Ponder 1.0.82, Flywheel 1.0.6, Registrate MC1.21-1.3.0+67; full Create+ modpack launch now developer-reported working |
| Create machine ownership — 2.14.2 | Persisted server-side machine locations/installations with builder UUID as first owner, owner sets, ownership lookups and attribution through `MachineOwnershipRegistry` / `CreateEventAttribution` |
| Create objective contracts — 2.14.2 | `KINETIC_ACTIVE` / `MACHINE_PROCESS`, machine and output registry IDs, speed threshold, `CreateObjectiveRouter`, processing and kinetic event models; debug simulation commands for isolated testing |
| Live kinetic monitoring — 2.14.3 | `CreateKineticMonitor`: samples registered installed kinetic machines every 10 server ticks; verifies loaded/valid machine, nonzero finite speed, sufficient power and no overstress; routes `KineticPowerEvent`; `/tamkinetic inspect` diagnostics |
| Mechanical Press — 2.14.4 | Create 6.0.10 Mixins observe real belt, loose-world-item and basin item-output processing; a bridge aggregates outputs and feeds `ProcessingCompletedEvent` to the existing router; developer reports tests passed and committed |
| Full Create+ IDE debugging | Working per developer report in isolated `run-createplus` runtime; no manual JAR copying required |
| Other Create processing | **2.14.5 implemented; basic progress confirmed by user** — Millstone, Crushing Wheels and Mixer |
| Player journal, networking and hints | **Not implemented:** planned 2.15, including spoiler-safe state sync and replacement for verbose chat notices |
| Two-player multiplayer and crash-atomic delivery | **Pending**; cannot infer correctness from single-player / normal-restart tests |

**Verification precision:** For 2.14.4 the developer reports a successful build, 100% success and a commit. No commit SHA, complete exact test log or independent repository inspection was supplied. Do not extrapolate that result into proven multi-account isolation, rare recipes, all item remainders or crash-safe exactly-once semantics. See I-014 and the next-chat handoff guide.

## Current JSON-defined prototype campaign and Create test fixture

The **default bundled server data pack** defines an ordered two-quest prototype. Quest IDs are persisted in player state; do not casually rename or reorder them in an existing save.

| Quest | Mandatory objective | Optional objective | Main rewards | Optional bonus before advancement |
|---|---|---|---|---|
| **The First Workshop** — `theageofmachinery:prototype_first_workshop` | Pick up 16 Cobblestone | Pick up 8 Oak Logs | 25 XP and 2 Iron Ingots | 10 XP |
| **The First Tools** — `theageofmachinery:prototype_first_tools` | Craft 1 Crafting Table | Craft 1 Furnace | 50 XP | 15 XP |

First-use ingredient metadata is authored in JSON, e.g. four planks for a Crafting Table and eight Cobblestone for a Furnace. The visible in-game ingredient checklist/journal remains planned for 2.15. Ground-item acquisition does not prove mining and does not include all inventory insertions.

A **separate development-only three-quest data pack**, `The_Age_of_Machinery_2.14.4_Test_Datapack.zip`, replaces the active campaign manifest in a disposable world and adds:

- **The First Powered Workshop** — `theageofmachinery:prototype_create_routing`.
- Mandatory `power_mechanical_press`: `KINETIC_ACTIVE`, machine `create:mechanical_press`, `minSpeed: 16`, `requiredAmount: 1`.
- Optional `press_iron_sheets`: `MACHINE_PROCESS`, machine `create:mechanical_press`, output `create:iron_sheet`, `requiredAmount: 3`.
- Rewards: 30 XP main and 15 XP optional, earned only when the optional objective is completed before manual advancement.

These are not ordinary public-release quest chapters. Install the ZIP (without extracting) into `run-createplus/saves/<TestWorld>/datapacks/` **before starting a new disposable world/session**. Use `/datapack list enabled`, `/tamruntime campaign` and `/tamruntime player` to verify activation. To advance the first two quests through debug commands for the 2.14.4 test fixture:

```mcfunction
/tamruntime progress gather_cobblestone 16
/tamruntime advance
/tamruntime progress craft_crafting_table 1
/tamruntime advance
/tamruntime player
```

Then **do not use** `/tamruntime progress press_iron_sheets ...` for real processing tests: that simulates progress and bypasses Create integration. The earlier 2.13.6 Chest-based third-quest test remains a distinct historical fixture.

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
├── milestones/                  # implementation notes for 2.13.x and 2.14.x
├── tests/                       # development-only data-pack fixtures
└── NEXT_CHAT_GUIDE.md           # precise current checkpoint and 2.14.5 handoff
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

## Development requirements and reproducible runtime

- **Minecraft:** 1.21.1; **NeoForge:** 21.1.233; **JDK:** 21.
- **Project:** `C:\the-age-of-machinery`; **mod ID:** `theageofmachinery`; **current Java package:** `com.TheAgeOfMachinery` (case-sensitive).
- **Create:** 6.0.10; original Maven version `6.0.10-280`; Ponder `1.0.82`; Flywheel `1.0.6`; Registrate `MC1.21-1.3.0+67`.
- **Modpack:** Create+ `6.0.0-alpha-f`, run **inside isolated development directory `run-createplus`** via the project's Gradle sync/preparation tasks. Original Modrinth profile is not a development output folder.
- **Working setup:** the user fixed a Java-environment problem that prevented full Create+ IDE launches. The exact external-agent fix is not documented; preserve the current working Gradle/JVM settings. The earlier Connector clean-Minecraft-artifact error should not be treated as the current blocker.

From the repository root:

```powershell
cd C:\the-age-of-machinery
.\gradlew.bat build
.\gradlew.bat runClient
```

Use your IDE's Gradle run/debug configuration to debug source code. `run-createplus/` is an isolated generated runtime and should remain excluded from Git. Back up valuable worlds before testing incompatible packs or persistent campaign/schema changes.

## Operator-only development commands

These commands are for testing and diagnosis, **not** the finished player experience. They must be restricted or removed before a public release. Some command names may depend on which test harnesses remain registered in a branch.

| Command | Purpose |
|---|---|
| `/tamruntime status` / `campaign` / `player` | Inspect session, ordered quest IDs and active objectives |
| `/tamruntime progress <objectiveId> <amount>` | **Debug-only** simulated objective progress |
| `/tamruntime advance` | Finalize eligible active quest (manual advancement) |
| `/tamruntime rewards` | Inspect *unlocked* reward entries and claim states |
| `/tamruntime claim first <rewardId>` / `second <rewardId>` / `quest <questId> <rewardId>` | Claim earned rewards; the quest-ID form supports every chapter quest |
| `/tamruntime verify` / `audit` | Check rejected operations and read-only player diagnostics |
| `/tamjson test` | JSON parser and negative-case tests |
| `/tamregistry test` | Whole-campaign validation tests |
| `/tamreloadtest` | Compatible/incompatible definition policy test; **spelling is reload** |
| `/tamruntime reloadstatus` | Whether mod campaign reload was applied or rejected |
| `/tamowner info <x> <y> <z>` | Inspect installation identity and owner attribution for a machine position |
| `/tamkinetic inspect <pos>` | Diagnose current speed, speed requirements, overstress and machine installation ownership (consult in-game syntax/completions for coordinates) |
| `/tamcreate kinetic` / `process` | 2.14.2 development-only synthetic Create objective routing; **not valid proof** of live 2.14.3/2.14.4 hooks |
| `/datapack list enabled` | Inspect Minecraft-enabled packs (vanilla command) |

Additional test harness commands exist for persistence, staged advancement and reward failures. Operator commands that display full campaign IDs are spoiler-sensitive and must not be accessible to ordinary players.

**Temporary progress feedback:** The active item-event router sends quest-update **chat/system messages on each matching pickup or craft event**. This is useful without a UI but will flood the chat during ordinary gameplay. The intended UI will receive relevant progress state/deltas and display restrained updates (e.g., completion notification or journal update) instead of one chat line per item. Production `LOGGER` errors/warnings for administrators are a separate channel and should not be disabled with chat feedback.

## Create integration architecture and milestones (2.14)

**Design invariant:** credit **verified server-side machine behavior**, not merely placement, possession, inventory movement or proximity. Reuse `CampaignRuntime`, `CampaignSavedData`, the objective router, and per-player quest state. Ownership is independent of quest state; no second progression engine.

### Implemented 2.14.2–2.14.4 path

```text
Create block placement / teardown (server-side)
  -> MachineOwnershipRegistry / MachineOwnershipSavedData
  -> installation identity at MachineLocation (dimension + BlockPos)
  -> builder UUID initially, internally a set of owner UUIDs

CreateKineticMonitor (2.14.3; server-side, every 10 ticks)
  -> validate current kinetic block / speed / overstress
  -> KineticPowerEvent

Mechanical Press Mixins (2.14.4; Create 6.0.10)
  -> onItemPressed (loose-world-item primary output)
  -> tryProcessOnBelt (actual output list, skip simulation)
  -> BasinBlockEntity.acceptOutputs (successful, nonsimulated basin outputs)
  -> MechanicalPressProcessingBridge
  -> ProcessingCompletedEvent

CreateEventAttribution.resolve(ServerLevel, CreateMachineEvent)
  -> trusted machine/installation and ownership lookup
CreateObjectiveRouter.route(ServerLevel, CreateMachineEvent)
  -> active-quest matching for each eligible owner
  -> per-player authoritative progress / deduplication
```

The 2.14.4 bridge groups outputs by item registry ID and quantity; it intentionally avoids crediting belt output twice when Create also invokes the `onItemPressed` callback. A basin-only context guards attribution. The initial implementation was authored against Create 6.0.10; verify actual final source before changing method signatures or Mixins. The use of fresh UUIDs for emitted output events does **not by itself** establish replay-safe, operation-level idempotency (I-014).

### Milestone ledger

| Milestone | Result | Status |
|---|---|---|
| **2.14.1** | Create dependency/runtime baseline; Create+ client discovered the mod; full-pack Gradle workflow restored after Java fix | **Completed (developer report)** |
| **2.14.2** | Trusted machine event contracts, builder ownership, persistent installation association, schema/parser routing and debugging commands | **Completed in development / committed (developer report)** |
| **2.14.3** | Live `CreateKineticMonitor` for working kinetic devices, with `/tamkinetic inspect` diagnostics | **Completed / built, tested and committed (developer report)** |
| **2.14.4** | Actual Mechanical Press output observation for belt, loose items and basin; integrated with ownership-aware `MACHINE_PROCESS` | **Completed / successful build, 100% tests and commit (developer report)** |
| **2.14.5** | Millstone, Crushing Wheels and Mechanical Mixer processing | **Completed; basic in-world progress confirmed by user (2026-10-28)** |
| **2.14.6** | Actual Create quest chapter authored as data-pack JSON; first-use ingredients, optional engineering challenges and rewards | **Planned** |
| **2.14.7** | Comprehensive integrated gameplay/restart/negative-condition tests in real Create+ environment | **Planned** |

### Ownership policy (I-013)

**Implemented first step:** player who places a supported machine is its initial registered owner; ownership is stored authoritatively and can be resolved by installation. Internally preserve **UUID owner sets** so the model is compatible with future multiple owners. Never infer ownership from the closest player or the collector of a produced item.

**Deferred multiplayer/cooperative feature:** a dedicated area-registration item will allow intentionally joining a registered workshop. Every registered player counts as an owner, any owner may open/close registration, and each owner receives progress **independently** only when their own *currently active* quest has a matching objective. Registration is not determined by proximity. Design for offline owners, teardown/replacement, overlapping areas and permission changes before implementation. Two-real-player testing is still pending.

**Processing caveats (I-014):** the completed Mechanical Press integration establishes real processing paths in the tested scenarios, not a guarantee covering every modded recipe, byproduct, failed output transfer, nested basin callback, client/server lifecycle or crash replay. Future processing machines must select one authoritative success point each and avoid double counting.

**Release gates:** malformed JSON diagnostics (I-011), chat spam (I-012), cross-restart schema migration (P-003), exactly-once reward delivery after crashes (P-005), two-player isolation (I-003), automated tests (I-004), and debug command permissions (P-006) remain tracked in `docs/FUTURE_CORRECTIONS.md`.

## Roadmap

- [x] **2.1–2.6:** Quest models, rewards, XP/item delivery.
- [x] **2.7–2.10:** Persistent data, revision-checked staged updates and guarded claims.
- [x] **2.11:** Server-scoped runtime, automatic player initialization and authoritative boundaries.
- [x] **2.12:** Vanilla crafting/pickup detection and active-quest event routing.
- [x] **2.13.1–2.13.6:** JSON schema, parsing, full campaign validation, server data packs and safe presentation-only live reload.
- [x] **2.14.1:** Create development dependencies and runtime.
- [x] **2.14.2:** Machine ownership, trusted Create event contracts, objective schema and router.
- [x] **2.14.3:** Live kinetic monitoring.
- [x] **2.14.4:** Mechanical Press real item processing; developer reports successful build, full milestone tests and commit.
- [ ] **2.14.5 (next):** Expand real Create processing to Millstone, Crushing Wheels and Mixer; prove quantities and no duplicate credit in automation.
- [ ] **2.14.6:** Author Create-focused quest chapter in JSON with meaningful engineering objectives, required ingredients and optional rewards.
- [ ] **2.14.7:** End-to-end Create+ compatibility and persistent-world regressions.
- [ ] **2.15:** Spoiler-safe client sync, Quest Journal, hints and ingredient/reward UI; remove/gate per-event chat spam.
- [ ] **Release hardening:** Invalid data reporting, campaign schema/world-save migration, two-player verification, automated tests, crash recovery, packaging and permissions.

**Current development is not a claim of release readiness.** Exact next actions and reproducible test instructions are in [`docs/NEXT_CHAT_GUIDE.md`](docs/NEXT_CHAT_GUIDE.md).

## License

**All Rights Reserved** under the currently recorded project configuration. A public GitHub repository alone does not grant rights to redistribute or modify the code; explicitly choose a license if those rights should be granted.
