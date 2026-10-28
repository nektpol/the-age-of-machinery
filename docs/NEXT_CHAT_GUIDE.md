# The Age of Machinery — fresh-chat continuation guide

> **Current work — 2.15.1 implemented, automated checks accepted (28 October 2026):** immutable `sync.PlayerCampaignView`, server-only `runtime.PlayerCampaignViewFactory` and `CampaignRuntime.getPlayerView(ServerPlayer)` now provide the authorized snapshot. Build/player-view privacy regressions passed; networking and UI are not implemented yet. Read [the contract](tests/2.15.1-player-view.md) before 2.15.2: use the runtime projection, preserve earned-ledger values, send cosmetic reload refreshes even at unchanged player revision, and keep payload recipient bound to the connected player. No new world/machine test is needed for 2.15.1. 2.14.7 ownership was accepted and committed as `9b58f6b`; its restart and multiplayer checks remain unreported. Final campaign design remains deferred. Provide commit title/description whenever the user confirms acceptance.

**Checkpoint:** 2026-10-28 · **2.14.7 committed; 2.15.1 implemented/automatically checked, automated checks accepted; campaign design provisional**
**Repo (local):** `C:\the-age-of-machinery`  
**Minecraft:** 1.21.1 · **NeoForge:** 21.1.233 · **Java:** 21  
**Full pack:** Create+ Modrinth `6.0.0-alpha-f` in an **isolated Gradle development client** `run-createplus/`  
**Create:** 6.0.10 (Maven `6.0.10-280`); Ponder 1.0.82, Flywheel 1.0.6, Registrate MC1.21-1.3.0+67  
**Mod ID:** `theageofmachinery` · **Java root:** `com.TheAgeOfMachinery`

> This guide consolidates the available 2.14.2 documentation, recovered test datapack and the 2.14.3/2.14.4 work discussed in chat. The current local source tree, exact latest commit SHA, and full test log are **not accessible from this document**, so confirm class/package names and recent edits against the checked-out project before refactoring. The developer reports successful build and tests for 2.14.4 and confirms it was committed. Do **not** ask to rebuild/retest the completed milestone without a concrete regression reason.

## 1. Context and completed architecture

The project is a spoiler-conscious quest/progression addon for Create/Create+, not a standalone new progression engine. Server-side `CampaignRuntime`, `CampaignSavedData` (NBT FormatVersion 3), `QuestManager` and an ordered JSON server data-pack campaign are the only authoritative quest state. One active quest per player, mandatory objectives gate **manual** advancement, optional objectives remain until advancement, earned rewards then become independently claimable. Do not disclose future quests or the current quest's unearned rewards in player-facing UI.

**2.12:** `CRAFT_ITEM`, `PICKUP_ITEM` route real crafting and ground-item pickup events into active quest objectives; debug `/tamruntime` supports controlled testing.

**2.13:** data-pack quest JSON with `formatVersion: 1`, `campaigns/main.json` ordered manifest, strict parser/assembler/registry validation, ingredient metadata, server data-pack loading, presentation-only in-session `/reload`. Structural live changes are rejected; structural modifications across restarts are **not** yet migration-safe (P-003). `requiredAmount: 0` previously caused a poorly diagnosed failure (I-011).

**2.14.1:** Gradle dependency/runtime integration for Create 6.0.10; development runs the **entire Create+ modpack** directly via `runClient` into isolated `run-createplus/` with IDE debugging. A separate agent resolved a previous **Java environment issue** that prevented launch. Its precise settings are not recorded; preserve the working Java/Gradle setup. Do not advise replacing it with manual JAR copying into Modrinth.

**2.14.2:** machine event data contracts and player attribution:

- `MachineLocation` uses dimension + block coordinates; persisted machine/installation ownership with `MachineOwnershipRegistry` and `MachineOwnershipSavedData`.
- Initial registered owner = the server-verified placing player. Store ownership as a set of UUIDs for future cooperative credit; no proximity or item-holder heuristics.
- Event types include `KineticPowerEvent`, `ProcessingCompletedEvent` and a common `CreateMachineEvent` contract.
- `CreateEventAttribution.resolve(ServerLevel, CreateMachineEvent)` resolves trusted installed machine and owner set.
- `CreateObjectiveRouter.route(ServerLevel, CreateMachineEvent)` matches **only each registered owner's own active quest** and applies server-authoritative, deduplicated progress.
- JSON objectives include `KINETIC_ACTIVE` (machine registry ID + minimum absolute RPM) and `MACHINE_PROCESS` (machine ID + item output ID).
- `/tamowner` and `/tamcreate` provide developer diagnostics and synthetic routing tests; synthetic events are not proof of actual production.

**2.14.3:** `CreateKineticMonitor` samples registered kinetic installations once per 10 server ticks; checks loaded, matching installation and valid `KineticBlockEntity`, nonzero finite effective RPM, no overstress and speed requirement. Valid activity produces a `KineticPowerEvent`. `/tamkinetic inspect` reports effective/theoretical RPM, overstress, speed requirements and registered installation ownership. Milestone was developer-reported built, tested and committed.

**2.14.4:** Mechanical Press real item processing on Create 6.0.10:

- `MechanicalPressBlockEntityMixin`: observes `onItemPressed` for loose-world-item outputs, `tryProcessOnBelt` for successful nonsimulated belt results, and establishes a scoped processing context for basin operations.
- `BasinBlockEntityMixin`: observes successful nonsimulated `acceptOutputs` while the press is applying a basin recipe.
- `MechanicalPressProcessingBridge`: validates the `ServerLevel`/installed press, aggregates `ItemStack` outputs by registry ID/count, emits `ProcessingCompletedEvent` and delegates to the existing ownership-aware router.
- The bridge avoids double counting Create's belt `onItemPressed` callback and the explicit belt processing output list.
- **Status:** developer reports build and 100% successful milestone tests, then committed. Final code may differ slightly from chat implementation; inspect actual committed source first.

## 2. Working commands, tests and data-pack content

Open PowerShell in the project root:

```powershell
cd C:\the-age-of-machinery
.\gradlew.bat build
.\gradlew.bat runClient
```

The `run-createplus` folder is a synchronized isolated development environment; the actual Modrinth Create+ profile must stay untouched. Use IDE breakpoints with source Gradle run/debug configuration. Do not propose excluding Fabric/Connector mods to make only a slim pack start. The previously encountered Connector clean-artifact failure was superseded by a Java environment fix.

Useful operator commands (confirm argument completion in the installed version):

```mcfunction
/tamruntime campaign
/tamruntime player
/tamruntime progress <objectiveId> <amount>
/tamruntime advance
/tamruntime reloadstatus
/tamowner info <x> <y> <z>
/tamkinetic inspect <x> <y> <z>
```

`/tamruntime progress` and `/tamcreate` are **synthetic** tests. Never use them when asserting that a real machine output completed an objective.

### Development-only 2.14.4 test datapack

Original file: `The_Age_of_Machinery_2.14.4_Test_Datapack.zip` (previously supplied; content verified). The ZIP contains:

```text
pack.mcmeta
 data/theageofmachinery/campaigns/main.json
 data/theageofmachinery/quests/prototype_first_workshop.json
 data/theageofmachinery/quests/prototype_first_tools.json
 data/theageofmachinery/quests/prototype_create_routing.json
```

The three-quest order is:

1. `theageofmachinery:prototype_first_workshop`: `gather_cobblestone` mandatory (16); `gather_logs` optional (8).
2. `theageofmachinery:prototype_first_tools`: `craft_crafting_table` mandatory (1); `craft_furnace` optional (1).
3. `theageofmachinery:prototype_create_routing` — **The First Powered Workshop**:
   - `power_mechanical_press`: mandatory, `KINETIC_ACTIVE`, `create:mechanical_press`, `minSpeed: 16`, amount 1.
   - `press_iron_sheets`: optional, `MACHINE_PROCESS`, machine `create:mechanical_press`, output `create:iron_sheet`, amount **3**.
   - Main reward 30 XP; optional press reward 15 XP.

Install the complete ZIP unextracted into `run-createplus/saves/<test-world>/datapacks/` and create/start a **disposable test world** with the pack active before gameplay. Verify `/datapack list enabled` and `/tamruntime campaign`; use a fresh player-state world when changing quest structures because live structural `/reload` is intentionally rejected. To reach the third quest in that fixture:

```mcfunction
/tamruntime progress gather_cobblestone 16
/tamruntime advance
/tamruntime progress craft_crafting_table 1
/tamruntime advance
/tamruntime player
```

For physical tests, place and power the press yourself, confirm ownership with `/tamowner info` and verify one actual produced Iron Sheet advances `press_iron_sheets` by exactly 1 (0/3, 1/3, 2/3, 3/3). Inspect actual Create recipe in Create+ if altered by the pack. Depot, moving belt, loose-world-item and item-producing basin compaction were included in the 2.14.4 test design. Do not infer that this fixture is the default released quest campaign: the built-in prototype has only two quests.

## 3. Historical 2.14.5 implementation plan — completed for basic progress detection

**Goal:** server-trusted `MACHINE_PROCESS` credit for **Millstone, Crushing Wheels and Mechanical Mixer**, including automation, batch sizes and byproducts, reusing the architecture that has already been tested with Mechanical Press.

### Implementation order

1. **Inspect live repository and APIs.** Read the current `build.gradle`, existing `MachineOwnershipRegistry`, `CreateObjectiveRouter`, all 2.14.4 bridge/Mixin sources, the Mixin JSON and the exact Create **6.0.10** classes. Do not guess API signatures, output timing or other-mod compatibility. Confirm which machine IDs the ownership registration currently supports before wiring any new adapters.
2. **Freeze 2.14.4 baseline.** Preserve existing Mechanical Press tests and ownership/kinetic checks. Establish debug log or breakpoint locations to confirm exactly one authoritative callback per successful operation.
3. **Map each machine's real success point.** For the Millstone and Crushing Wheels identify output creation or processing-finalization callbacks. For Mechanical Mixer distinguish its basin recipe success from Mechanical Press compacting and other basin machines. Record recipe input consumed, actual output list, quantities, position and server tick/operation identity.
4. **Choose a small shared processing-event adapter only after mapping.** Normalize trusted `(machine location, installation identity, machine block ID, output item ID, count, operation identity)` and feed `ProcessingCompletedEvent`/`CreateObjectiveRouter`; do not add a second quest manager or poll every inventory each tick. Make no assumptions that merely rotating machines guarantee a successful recipe.
5. **Address operation identity and scope safety.** Initial 2.14.4 draft used fresh UUIDs per output and a `ThreadLocal` with HEAD/RETURN cleanup for basin context. Review **committed** implementation and ensure no duplicate reports when different hooks fire, no stale context on exceptions or nested operations, no count for simulation, and no credit for leftovers/failed transfers.
6. **Tests in a dedicated data-pack world.** Build complete updated quest fixture with machine-specific optional outputs and `requiredAmount > 1`. If schema/datapack changes, provide the **full final `quest.schema.json`** and **ready-to-install whole ZIP**, not partial trigger fragments. Use a fresh test world for structural changes; `/reload` is text-only.
7. **Regression and commit.** Build, run the **full** Create+ `runClient` and manual tests; verify 2.14.4 press/2.14.3 kinetic still work. Then update README/FUTURE_CORRECTIONS and commit after user confirms results.

### Minimum acceptance matrix for 2.14.5

| Case | Expected |
|---|---|
| Millstone valid recipe completed | Credit produced item amount exactly once to registered owner if active objective matches |
| Crushing Wheels valid processing completed | Same, including whether bulk inputs generate multiple outputs |
| Mixer with Basin produces valid item output | Credit only actual successful mixing output to the correct mixer installation |
| Wrong item / wrong machine / inactive quest | No credit |
| Zero speed, overstress, no recipe, interrupted operation or simulation | No processing credit |
| Output count > 1, multiple distinct outputs/byproducts | Count precisely by item registry ID and actual quantity, not number of callbacks |
| Two or more production events in rapid succession | No duplicate credit and no missed results |
| Item pickup or `/give` of the same result | Does not masquerade as a processing event |
| Unregistered/destroyed/replaced machine | No arbitrary owner credit; installation identity checked |
| Full Create+ after changes | Existing Mechanical Press and live kinetic tests still pass, no Mixin conflicts |
| Save/restart and rejoin | No unexpected duplicate or lost quest progress; record precise limits of crash recovery |

**Known deferred policy:** cooperative *area registration item* and any-owner open/close rule are **not** in 2.14.5. Internal owner sets still matter for the future, and two-real-player behavior remains an untested explicit acceptance gate.

## 4. Other roadmap and risks not to forget

- **2.14.6:** author first genuine Create quest chapter via JSON/data packs, meaningful engineering tasks, first-use ingredient checklist **with quantities**, optional challenges/rewards and manual advancement.
- **2.14.7:** comprehensive Create+ in-world/regression testing, persistence, mods and world boundaries.
- **2.15:** spoiler-controlled client state/networking, Quest Journal/HUD, ingredient checklist UI, three graduated hints and independently claimable earned rewards. Replace noisy per-item quest messages (I-012).
- **I-011:** malformed pack (`requiredAmount: 0`) crashed/failed without useful visible diagnostics; determine startup vs `/reload` path, capture log and fix clear rejection.
- **I-003:** two player accounts simultaneously in one server **NOT TESTED**; do not mark passed.
- **P-003:** persisted campaign structural compatibility across world restarts not yet solved.
- **P-005:** XP/item reward delivery and `SavedData` write are not a crash-atomic transaction; no exactly-once guarantee after a crash.
- **I-014/I-015:** processing output fidelity, callback double-counting, stable operation ID and exception-safe basin scope still need scrutiny when generalizing beyond press.
- **I-004/P-006:** automated regressions and debug command authorization/release gating remain open.

See `docs/FUTURE_CORRECTIONS.md` for complete issue ledger; **preserve all existing issue IDs** when continuing.

## 5. Development style and collaboration rules

- Java code: **4 spaces**, **Allman** opening braces for classes, methods, loops and multi-statement blocks.
- Single-statement guard `if`s are one line with **no braces** (`if (x == null) return;`). For multi-statement conditionals, use braces with statements on their own lines.
- Keep function calls, constructor calls and assignments **on a single line**, even if long; avoid arbitrary wrapping to fit 80 columns.
- Prior sample classes `MachineOwnershipSavedData` and `MachineOwnershipRegistry` demonstrate **formatting only**, not required architecture, Optional usage or validation patterns.
- For Java changes, supply **complete runnable code** and clear compilation/in-game tests. Do not claim build success or commit before user confirmation.
- For JSON/schema/datapack modifications: user requires the **complete final schema and full installable ZIP**, never a snippet and a vague "activate a matching quest" instruction.
- Keep milestones bounded; explain the real hook/success point first, then implementation, then concise and reproducible tests with exact commands.
- User is comfortable with C++/Unreal Engine and wants technical precision without patronizing gloss or redundant setup questions.

## 6. Suggested first user message in a fresh chat

> Continue **The Age of Machinery** at milestone **2.14.6**. 2.14.5 basic progress testing was accepted by the user on 28 October 2026; author the first real Create campaign chapter next. The following older implementation prompt is historical: Continue at milestone **2.14.5**. Milestones 2.14.1–2.14.4 are completed, tested and committed. Full Create+ 6.0.0-alpha-f works through Gradle `runClient` with IDE debugging; don't change the Java/Gradle launch solution. Read `README.md`, `docs/FUTURE_CORRECTIONS.md` and this handoff first. We want real machine processing completion detection for **Millstone, Crushing Wheels and Mechanical Mixer**, built on our existing `ProcessingCompletedEvent`, machine ownership and `CreateObjectiveRouter`. Start by examining current source and Create 6.0.10 processing paths, then design success hooks that avoid simulation, canceled operations and double counting. Follow my Java formatting preferences, include concrete in-game tests, and don't invent incomplete quest JSON. The 2.14.4 press integration must remain intact.

**Key instruction:** Next is **2.14.6**. Keep tests practical: a short numbered guide, small quantities, and a clear pass condition. Do not ask the user to repeat accepted 2.14.5 progress tests or produce thousands of items. Preserve the full Create+ Gradle workflow.
