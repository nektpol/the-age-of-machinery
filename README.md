# The Age of Machinery

*A spoiler-conscious, Create-focused quest and progression mod for Minecraft Java Edition 1.21.1 (NeoForge).*

> **Development status — 26 October 2026:** Early development. The Java quest/reward domain model and Minecraft integrations are working in isolated tests, but there is **no complete playable quest campaign or player-facing quest/rewards UI yet**.

## Project goals

- One active quest per player, with mandatory objectives and optional challenges.
- Manual quest advancement after the mandatory objectives are completed.
- No future-quest previews or reward previews before finishing a quest; only earned rewards are revealed.
- Three levels of optional spoiler-controlled hints and first-use ingredient checklists with quantities.
- Independent, server-owned player progression keyed by UUID, including multiplayer support in the intended design.
- Rewards earned at quest finalization, grouped by quest, claimable at any later time. Each entry is indivisible (for example, 100 XP, 50 XP and 5 cogwheels can be claimed separately, but not fractionally).
- Engineering-oriented objectives emphasizing functional Create machinery and discovery rather than grind.

## Current implementation

| Area | Status |
|---|---|
| Quest definitions, objectives and progression model | Implemented in Java; build verified |
| Per-player quest tracking and linear campaign coordinator | Implemented in-memory; progression hardening pending |
| XP and item reward definitions, entitlement inventory and claim results | Implemented; build verified |
| Minecraft XP delivery (`ServerPlayer`) | Manually verified in-game: 100 XP received |
| Full item reward delivery | Manually verified: five iron ingots, full-inventory rejection with no partial delivery, and successful retry |
| Debug reward claiming via `RewardManager` | Manually verified: three entries, independent claims, duplicate-claim rejection |
| Quest progress NBT codec and world persistence | Implemented; mandatory/optional progress and completion survived world restarts in a debug test |
| Reward inventory NBT codec and world storage | Implemented and built; **reward restart test pending** |
| Live quest/reward managers using the saved world data | **Not yet integrated** |
| Create objective detection, JSON campaign, client journal and rewards UI | **Not yet implemented** |
| Automated tests, multiplayer and crash consistency | **Pending** |

This distinction matters: debug/test commands demonstrate individual systems; they do not constitute a finished campaign or guarantee crash-safe reward claiming.

## Development stack

- Minecraft Java Edition **1.21.1**
- **NeoForge 21.1.233** as the currently tested Create+ compatibility target
- **Java 21** with Gradle / ModDevGradle
- Visual Studio Code
- Create/ Create+ as intended gameplay ecosystem; development `runClient` does not automatically include the Modrinth Create+ modpack
- Package currently used in source: `com.TheAgeOfMachinery`
- Mod ID: `theageofmachinery`

## Build and local development

Install JDK 21 and build from the project root:

```powershell
.\gradlew.bat build
```

The compiled mod JAR is generated under `build/libs/`.

Launch an isolated development client:

```powershell
.\gradlew.bat runClient
```

The development client and your Modrinth profile have separate mods and saves. When testing in Create+, back up the world and match your NeoForge/Create versions before adding or replacing the built mod JAR.

## Architecture at a glance

```text
quest/
    QuestDefinition + QuestObjective          immutable definitions
    QuestProgress                             progress for one quest
    PlayerQuestProgress                       all quests for one player
    QuestManager                              current in-memory campaign coordinator

reward/
    RewardDefinition                          sealed XP / item reward interface
    ExperienceReward + ItemReward             immutable reward definitions
    RewardEntry + PlayerRewardInventory       earned and claimed entries
    RewardManager                             current in-memory claim coordinator

integration/
    MinecraftRewardDelivery                   ServerPlayer XP / inventory insertion

persistence/
    PlayerQuestProgressCodec                  quest state <-> NBT
    PlayerRewardInventoryCodec                entitlement state <-> NBT
    CampaignSavedData                         SavedData schema v3, keyed by UUID

command/
    RewardTestCommand                         /tamtest
    RewardClaimTestCommand                    /tamrewardtest
    SaveTestCommand                           /tamsavetest
    QuestPersistenceTestCommand               /tamquestsave
```

**Important design boundary:** `CampaignSavedData` persists player state, while the current managers still keep separate maps. The next integration work will make world-owned saved data authoritative and coordinate dirty marking. Claim delivery is not yet crash-atomic with Minecraft inventory/XP saving.

## Development-only commands

Commands currently use operator permission level 2 and are meant for disposable test worlds, not normal campaign gameplay:

| Command | Purpose |
|---|---|
| `/tamtest xp` / `/tamtest item` | Grant isolated test XP or 5 iron ingots |
| `/tamrewardtest unlock` | Unlock 100 XP, 5 iron ingots, and 50 bonus XP in a temporary in-memory inventory |
| `/tamrewardtest status` | Display current debug claim states |
| `/tamrewardtest claim xp`, `iron`, or `bonus` | Claim one entry through the reward manager |
| `/tamsavetest increment` / `value` | Inspect the simple `SavedData` test counter |
| `/tamquestsave init`, `status`, `progress main`, `progress optional`, `complete` | Test persisted quest state and objective progress |

Debug unlock/grant commands must be disabled or gated before public release.

## Roadmap

- [x] Bootstrap NeoForge project; build and load in the Create+ Modrinth profile.
- [x] Implement quest and reward domain models, including server-side XP and full-item delivery.
- [x] Verify XP, items, independent claiming, duplicate rejection and full-inventory behavior in-game.
- [x] Implement player quest NBT persistence and verify restoration across world restarts.
- [x] Implement reward inventory codec and `CampaignSavedData` schema v3 (build and commit confirmed).
- [ ] Verify **reward unlock and claim state** across a world restart (Milestone 2.7.8).
- [ ] Unify live managers with authoritative world save data and dirty marking.
- [ ] Implement safe coordinated quest finalization and earned-reward unlocking.
- [ ] Add JSON quest definitions, deterministic campaign order and Create event detection.
- [ ] Add spoiler-controlled client synchronization, quest journal, rewards UI and hints.
- [ ] Add automated tests, multiplayer verification, migration/recovery strategy and release gating for debug commands.

For prioritized issues, deferred corrections and acceptance criteria, see [`docs/FUTURE_CORRECTIONS.md`](docs/FUTURE_CORRECTIONS.md).

## License

**All Rights Reserved** under the current generated project configuration. The repository being publicly readable does **not** by itself grant permission to reuse or redistribute its code. Add an explicit license if you decide to open-source it.
