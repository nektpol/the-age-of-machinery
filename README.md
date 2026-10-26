# The Age of Machinery

*A spoiler-conscious, Create-focused quest and progression mod for Minecraft Java Edition 1.21.1 (NeoForge).*

> **Development checkpoint — 26 October 2026 · Milestone 2.11.6.** The **server-side quest/reward prototype is functional**: quests initialize on login, progress can be updated through development commands, advancement automatically unlocks earned rewards, rewards can be claimed independently, and state survives world restarts. **Real gameplay-triggered objectives, Create-specific integration, the player-facing journal, networking, and the complete campaign are not yet implemented.** Two-player multiplayer testing is **pending** because a second account was unavailable.

## What this mod is intended to do

The Age of Machinery guides players through a discovery-oriented progression of **Create** engineering challenges in Minecraft. It is a quest experience designed for the Create/Create+ ecosystem, not a generic quest engine.

- **One active quest per player**, with mandatory and optional objectives and completed quest history.
- **Manual advancement** after mandatory objectives are complete; optional objectives remain available until the player chooses to advance.
- **No spoilers:** future quests and the current quest's rewards remain undisclosed during play. Only *earned* rewards are revealed when a quest is finalized.
- **Independent, persistent reward claims:** main and earned optional rewards are grouped by quest. Each entry can be claimed separately, but never partially; unclaimed rewards survive subsequent quests and world restarts.
- **Hints and material lists:** three levels of optional hints are planned. The first time a quest asks players to craft/build something, its instructions should include ingredient quantities.
- **Server-owned UUID progression**, intended for multiplayer; two-player isolation has **not** been tested yet.
- **Functional engineering objectives**, ultimately detecting real Minecraft and Create gameplay rather than relying on debug commands.

## Current implementation

| Subsystem | Status as of 2.11.6 |
|---|---|
| Java quest models, mandatory/optional objectives, per-player history | Implemented; tested |
| XP and item rewards, independently claimed by quest | Implemented; tested in-game |
| Full-inventory refusal (all-or-nothing item reward) | Verified in-game, including retry |
| Minecraft `SavedData` and NBT codecs (`FormatVersion = 3`) | Implemented; normal world-restart tests passed |
| Coordinated advancement: reward calculation → staged state → one in-memory commit → next quest | Implemented; tested |
| Detached state snapshots, revision-checked writes, duplicate/reentrant claim guards | Implemented; regression and failure-injection tests passed |
| One server-scoped `CampaignRuntime` and automatic login initialization | Implemented; tested across world restarts |
| Built-in two-quest prototype registered on server startup | Implemented; tested via debug commands |
| Rejected invalid objectives, invalid amounts, and nonexistent rewards | Verified in-game |
| Two-player multiplayer isolation | **Pending — not tested** |
| Automatic crafting/collection objective tracking | **Not implemented** |
| Create machinery/automation detection | **Not implemented** |
| JSON quest definitions, final campaign content | **Not implemented** |
| Client network synchronization, Quest Journal, rewards screen and hint UI | **Not implemented** |
| Crash-atomic XP/item delivery and automated test suite | **Not implemented** |

**Verification caveat:** The developer reported successful builds, in-game tests, world-restart tests, and commits through 2.11.6. These are manual development checks, not an independent source audit or an automated CI test suite. The two-player scenario described in 2.11.6 was **not performed**.

## Current prototype campaign

The runtime registers two **development-only, server-side definitions**; they are not the final Create progression:

1. **The First Workshop** (`theageofmachinery:prototype_first_workshop`): mandatory *Obtain 16 Cobblestone*, optional *Obtain 8 Oak Logs*. Earned main rewards: 25 XP and 2 Iron Ingots; earned optional reward: 10 XP.
2. **The First Tools** (`theageofmachinery:prototype_first_tools`): mandatory *Craft a Crafting Table* (4 planks), optional *Craft a Furnace* (8 cobblestone). Main reward: 50 XP; optional reward: 15 XP.

Player login starts the first quest automatically. At present, **the game does not detect the gathering or crafting actions**; an operator uses `/tamruntime progress <objectiveId> <amount>` to simulate an event. The player still manually advances, and the reward system handles independent claims after advancement. The ingredient counts shown in this prototype are text examples; a generalized recipe/ingredient system is pending.

## Architecture

```text
MinecraftServer
  └── CampaignRuntime                     one instance per server session
        ├── BuiltInCampaign / CampaignDefinition
        │     └── QuestRewardDefinition   ordered, validated server definitions
        ├── CampaignAdvancementService    one registered QuestManager internally
        │     └── QuestManager            objective progress; no separate player map
        ├── RewardManager                 unlock and controlled reward claiming
        └── CampaignSavedData             one authoritative UUID-keyed player store
              ├── PlayerQuestProgressCodec
              └── PlayerRewardInventoryCodec

CampaignPlayerEvents                    server-side login initialization
MinecraftRewardDelivery                 real ServerPlayer XP/item delivery
command/*                               operator-only development test harnesses
```

Advancement validates mandatory objectives, calculates optional rewards, modifies **detached staged copies**, and replaces quest/reward state together using a revision check. Normal claim execution blocks reentrant claims and writes the new claim state without a fallible post-delivery revision check. All live gameplay calls go through the server-owned runtime, which checks server-thread execution and derives player identity from `ServerPlayer`.

**Important limitation:** Minecraft player XP/inventory saves and this mod's `SavedData` saves do **not** form one durable transaction. Abrupt crashes or delivery callbacks that throw after side effects may leave uncertain outcomes. Do not advertise exactly-once reward delivery or crash recovery yet; see [`docs/FUTURE_CORRECTIONS.md`](docs/FUTURE_CORRECTIONS.md).

## Technical requirements and development

- **Minecraft:** Java Edition 1.21.1
- **Mod loader:** NeoForge 21.1.233 (tested project target)
- **Java:** JDK 21
- **Build:** Gradle / ModDevGradle on Windows
- **Editor:** VS Code
- **Mod ID:** `theageofmachinery`
- **Current Java package:** `com.TheAgeOfMachinery` (case-sensitive; do not silently rename)
- **Create/Create+:** intended compatibility and forthcoming gameplay integration; **Create event/API integration is not yet implemented**.

From the repository root in PowerShell:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

Build artifacts are under `build/libs/`. The development client does **not** automatically include the mods or saves from a Modrinth Create+ profile. Back up any existing world before testing a new JAR; ensure the loader and mod dependencies match.

## Development commands

These commands are development/test tools and generally require Minecraft **operator permission level 2**. They must be disabled, gated, or removed before public release.

| Command | Purpose |
|---|---|
| `/tamruntime status` | Check active server runtime session |
| `/tamruntime campaign` | Inspect the registered prototype campaign (operator-only; spoiler-sensitive) |
| `/tamruntime player` | Show the current player's active objectives |
| `/tamruntime progress <objectiveId> <amount>` | **Simulate** objective progress, not a real gameplay detector |
| `/tamruntime advance` | Manually finalize the ready quest, unlock earned rewards, and activate the next |
| `/tamruntime rewards` | Show unlocked reward entries |
| `/tamruntime claim first <rewardId>` / `second <rewardId>` | Claim an earned reward from the named prototype quest group |
| `/tamruntime verify` | Check invalid objective/progress/reward operations do not mutate state |
| `/tamruntime audit` | Read-only overview of connected players' quest/reward group states |

Additional `/tamtest`, `/tamrewardtest`, `/tamsavetest`, `/tamquestsave`, `/tamrewardsave`, `/tammanagertest`, `/tamadvancetest`, `/tamstatetest`, `/tamquestrevision`, `/tamrewardrevision`, and `/tamfailuretest` commands exist for isolated persistence, claim, concurrency, and failure-injection tests. These are **not** ordinary gameplay commands.

## Roadmap

- [x] Quest/reward domain model and real Minecraft XP/item delivery (2.1–2.6).
- [x] World persistence and reward claim restoration across restarts (2.7).
- [x] Persistent manager integration and coordinated quest advancement (2.8–2.9).
- [x] Staged advancement, revision checks, delivery guards, failure injection, and removal of unconditional storage APIs (2.10).
- [x] Server runtime lifecycle, built-in prototype registration, automatic login initialization, player-bound operations, and manual runtime verification (2.11).
- [ ] **2.12 — Real Minecraft gameplay event integration:** crafting detection, item acquisition semantics, objective event routing, and playable prototype without debug progress commands.
- [ ] **2.13 — Data-driven campaign:** validated JSON quest/reward definitions and stable objectives/targets.
- [ ] **2.14 — Create integration:** identify real Create recipes, mechanisms, processing, and functional automation milestones.
- [ ] **2.15 — Client synchronization and UI:** spoiler-safe networking, Quest Journal, reward claiming panel, hint levels, ingredient checklists.
- [ ] **Release hardening:** automated tests, two-player dedicated-server verification, version/migration tests, delivery recovery policy, debug-command gating.

**Multiplayer test status:** Per-player UUID state and online player auditing are implemented, but **two-player isolation remains pending**. It was not possible to run a test with two accounts at this checkpoint.

See [`docs/FUTURE_CORRECTIONS.md`](docs/FUTURE_CORRECTIONS.md) for tracked corrections, open decisions, and acceptance criteria.

## License

**All Rights Reserved** under the currently recorded project configuration. A public GitHub repository does not automatically grant permission to redistribute or modify the code. Add an explicit license if the intended permissions change.
