# The Age of Machinery

*A spoiler-conscious, Create-focused quest and progression mod for Minecraft Java Edition 1.21.1 (NeoForge).*

> **Development checkpoint — 26 October 2026 · Milestone 2.12.4.** The **server-side two-quest prototype now responds to real Minecraft gameplay**: picking up Cobblestone/Oak Logs and crafting a Crafting Table/Furnace can advance active objectives automatically. Player login initializes the first quest, advancement remains manual, earned rewards unlock after finalization, and reward claims and progress persist across normal world restarts. **Create-specific functionality, JSON/data-pack campaign loading, networking, the Quest Journal and the final campaign are still planned.** Two-player multiplayer verification is **pending**, because a second account was unavailable.

## What this mod is intended to do

The Age of Machinery guides players through a discovery-oriented progression of **Create** engineering challenges in Minecraft. It is a quest experience designed for the Create/Create+ ecosystem, not a general-purpose quest engine.

- **One active quest per player**, with mandatory and optional objectives and completed history.
- **Manual advancement:** satisfying mandatory objectives enables advancement but does not trigger it. Optional objectives remain available until the player advances.
- **No spoilers:** future quests and the current quest's rewards are not revealed during ordinary play. Only actually *earned* rewards are revealed after finalization.
- **Independent persistent reward claims:** each earned reward can be claimed independently, but not partially; unclaimed rewards remain available after later quests and normal world restarts.
- **Discovery support:** three progressively more specific hints are planned; a first-time crafting/building task must show a checklist of ingredients with quantities.
- **Server-owned UUID progression:** designed for independent multiplayer progress, although simultaneous two-player behavior is not yet verified.
- **Gameplay-driven objectives:** real Minecraft pickups and crafting are implemented for the prototype; Create engineering achievements are a future phase.

## Current implementation

| Subsystem | Status after 2.12.4 |
|---|---|
| Quest/reward models, mandatory/optional objectives, per-player history | Implemented |
| Individual XP/item claims and full-inventory all-or-nothing refusal | Implemented; in-game reward tests previously reported successful |
| `CampaignSavedData` NBT persistence (`FormatVersion = 3`) | Implemented; normal world-restart tests reported successful |
| Coordinated advancement, detached snapshots, revision checks, reentrant claim guards | Implemented; prior development/failure-injection tests reported successful |
| Server-scoped `CampaignRuntime`, automatic player login initialization, player-bound API, server-thread checks | Implemented |
| Ordered two-quest built-in prototype | Implemented; registered on server startup |
| **Automatic crafting detection** | **Implemented:** `PlayerEvent.ItemCraftedEvent` on the server |
| **Automatic ground-item pickup detection** | **Implemented:** `ItemEntityPickupEvent.Post`, using actual acquired quantity |
| **Definition-driven objective routing** | **Implemented:** `ObjectiveTriggerType`, `ObjectiveTrigger` and `CampaignObjectiveRouter` for `CRAFT_ITEM`/`PICKUP_ITEM` |
| Manual advancement and post-completion reward claims | Implemented via development commands; no player-facing GUI yet |
| Two-player multiplayer isolation | **Pending — not tested** |
| JSON/data-pack definitions and reload compatibility | **Not implemented — planned for 2.13** |
| Create machinery, processing and engineering achievements | **Not implemented — planned for 2.14** |
| Client synchronization, Quest Journal, hint/material/reward UI | **Not implemented — planned for 2.15** |
| Crash-atomic XP/item delivery, adversarial networking tests, automated CI/GameTest suite | **Not established** |

**Verification scope:** The developer reported successful builds and commits through **2.12.4**. Earlier in-game and normal restart checks were reported for quest progression and rewards. The detailed step-by-step 2.12.4 gameplay verification log was not supplied for independent inspection. **A successful build alone does not prove every item-pickup/crafting edge case** (for example, shift-click/bulk crafting). Two-player tests and crash-timing tests remain open.

## Current prototype campaign

The runtime currently registers two **development-only, server-side** quests. These are not the final Create campaign:

| Quest | Mandatory objective | Optional objective | Earned main rewards | Optional reward if completed before advancement |
|---|---|---|---|---|
| **The First Workshop** (`theageofmachinery:prototype_first_workshop`) | Pick up 16 Cobblestone | Pick up 8 Oak Logs | 25 XP, 2 Iron Ingots | 10 XP |
| **The First Tools** (`theageofmachinery:prototype_first_tools`) | Craft a Crafting Table (4 planks) | Craft a Furnace (8 cobblestone) | 50 XP | 15 XP |

Objective requirements are stored in `QuestObjective` records. The gameplay trigger is now explicit (`ObjectiveTrigger`): item-based objectives specify a trigger type and a validated item registry ID; existing four-argument constructors continue to create `MANUAL` objectives for compatibility. **Only objectives of the currently active quest receive progress.**

For now, *gathering* specifically means **successfully picking up dropped items**. It does not imply that the player mined or created those items, and it does not count every way to move items into inventory. The pickup listener counts the successful transfer amount (original stack minus remaining stack). Crafting detection counts the crafted output stack reported by the event; bulk/shift-click correctness deserves an explicit regression test.

The two prototype crafting requirements are still written as descriptive text. A structured, quantity-aware ingredient schema and player-facing checklist are planned; they are **not** yet implemented.

## Architecture

```text
MinecraftServer
  └── CampaignRuntime                       one instance per server session
        ├── BuiltInCampaign / CampaignDefinition
        │     └── QuestRewardDefinition     ordered server definitions
        │           └── QuestObjective      ID, count, optional flag, ObjectiveTrigger
        ├── CampaignAdvancementService      contains one registered QuestManager
        │     └── QuestManager              objective progress; no separate player map
        ├── RewardManager                   earned rewards and controlled claiming
        └── CampaignSavedData               authoritative UUID-keyed state (NBT v3)
              ├── PlayerQuestProgressCodec
              └── PlayerRewardInventoryCodec

CampaignPlayerEvents                        initializes new players on server login
CampaignCraftingEvents                      server crafting event adapter
CampaignItemPickupEvents                    successful ground-pickup event adapter
          └── CampaignObjectiveRouter       matches active objective by type + item ID
                    └── CampaignRuntime     applies authoritative objective progress
MinecraftRewardDelivery                    delivers actual XP/items to ServerPlayer
command/*                                 operator-only development test harnesses
```

Advancement checks mandatory objectives, calculates earned optional rewards, stages detached quest/reward state, and commits them together with a revision check. Claim operations are server-owned and guard against duplicate/reentrant claims. Gameplay updates go through `CampaignRuntime`, with player identity derived from `ServerPlayer` and server-thread validation.

**Important limitation:** Minecraft player inventory/XP data and this mod's `SavedData` are **not** one durable transaction. A crash or callback failure after reward delivery may leave uncertain outcomes. Exactly-once delivery and crash recovery are **not** claimed. See [`docs/FUTURE_CORRECTIONS.md`](docs/FUTURE_CORRECTIONS.md).

## Technical requirements and development

- **Minecraft:** Java Edition 1.21.1
- **NeoForge:** 21.1.233 (project development target)
- **Java:** JDK 21
- **Build:** Gradle / ModDevGradle on Windows
- **Editor:** VS Code
- **Mod ID:** `theageofmachinery`
- **Current Java package:** `com.TheAgeOfMachinery` (case-sensitive; do not silently rename)
- **Create/Create+:** intended integration; actual Create dependencies and compatibility still need verification in the target modpack.

From the repository root in PowerShell:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

Compiled JARs appear under `build/libs/`. The NeoForge development client does **not** automatically contain mods or worlds from a separate Modrinth Create+ profile. Check dependency versions and back up worlds before testing updated builds.

## Development commands

These are **operator/debug tools**, not the finished player experience. They generally require Minecraft permission level 2 and must be disabled, separated, or strictly gated before a public release.

| Command | Purpose |
|---|---|
| `/tamruntime status` | Inspect active server runtime session |
| `/tamruntime campaign` | Operator-only inspection of prototype quest ordering (spoiler-sensitive) |
| `/tamruntime player` | Show the current active quest and objectives |
| `/tamruntime progress <objectiveId> <amount>` | Simulate arbitrary objective progress **for debugging only**; no longer required for the two prototype objectives |
| `/tamruntime advance` | **Manually** finalize an eligible quest and unlock earned rewards |
| `/tamruntime rewards` | List unlocked reward entries and claim states |
| `/tamruntime claim first <rewardId>` / `second <rewardId>` | Claim earned prototype rewards |
| `/tamruntime verify` | Verify invalid objective/reward operations do not change state |
| `/tamruntime audit` | Read-only player-state diagnostics for operators |

Other `/tamtest`, `/tamrewardtest`, `/tamsavetest`, `/tamquestsave`, `/tamrewardsave`, `/tammanagertest`, `/tamadvancetest`, `/tamstatetest`, `/tamquestrevision`, `/tamrewardrevision`, and `/tamfailuretest` commands are isolated test harnesses, **not** ordinary gameplay features.

## Roadmap

- [x] **2.1–2.6 — Domain and reward systems:** quests, objectives, rewards, Minecraft XP/item delivery.
- [x] **2.7–2.10 — Persistence and transaction integrity:** NBT v3, shared authoritative state, staged advancement, guarded claims and validation.
- [x] **2.11 — Server runtime:** lifecycle, prototype registration, shared operations, login initialization and player/server authority.
- [x] **2.12 — Real Minecraft gameplay integration:** crafting detection (2.12.1), successful ground-item acquisition (2.12.2), reusable objective event routing (2.12.3), gameplay vertical-slice verification milestone (2.12.4). **Builds and commits reported complete; specific edge-case tests remain tracked.**
- [ ] **2.13 — JSON/data-pack campaigns:**
  - [ ] **2.13.1** JSON schema, explicit campaign order, reward and structured ingredient metadata.
  - [ ] **2.13.2** JSON parsing and conversion to existing validated Java domain models.
  - [ ] **2.13.3** Atomic whole-campaign validation: unique IDs, references, registry identifiers, reward associations.
  - [ ] **2.13.4** Minecraft data-pack loading, runtime integration, prototype content migration to JSON without changing saved IDs.
  - [ ] **2.13.5** Safe `/reload` behavior and definition/save compatibility rules for existing players.
  - [ ] **2.13.6** End-to-end JSON campaign tests, including invalid data, restarts and a new quest without recompilation.
- [ ] **2.14 — Create integration:** validate Create/Create+ versions, detect real processing and functional engineering achievements.
- [ ] **2.15 — Spoiler-safe networking and UI:** Quest Journal, ingredient checklist, hints, manual advancement and grouped reward claims.
- [ ] **Release hardening:** automated regression tests, two-player dedicated-server verification, save migration checks, crash recovery policy, permission/diagnostic restrictions.

### Data-pack transition rules for 2.13

The planned JSON loader must **not** reinitialize player progress or recreate rewards merely because definitions reload. Quest/objective/reward IDs must remain stable where existing saves refer to them. Load and validate a **complete** candidate campaign before activating it; do not partially replace definitions. Decide how to reject or migrate incompatible order, count, target, and reward changes while players are progressing. `/reload` support is a separate milestone, not implied by parsing JSON.

Campaign definitions and reward lists are server-managed. The future client protocol must send only the permitted active quest, completed history, and earned rewards; operator debug visibility does not define normal client visibility. Note that content embedded in a distributed client JAR is not confidential from someone inspecting the JAR.

**Multiplayer test status:** Simultaneous two-player progression remains **PENDING / NOT TESTED** due to lack of a second account. That limitation does not block 2.13, but it remains a release verification gate.

See [`docs/FUTURE_CORRECTIONS.md`](docs/FUTURE_CORRECTIONS.md) for the issue ledger and acceptance criteria.

## License

**All Rights Reserved** under the currently recorded project configuration. A public GitHub repository does not automatically grant redistribution or modification rights. Add a license explicitly if you later want to permit either.
