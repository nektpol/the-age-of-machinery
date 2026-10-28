# Milestone 2.13.1 — JSON Campaign Format v1

**Project:** The Age of Machinery — Minecraft 1.21.1 / NeoForge 21.1.233  
**Status:** Contract and examples only. **The running Java mod still uses `BuiltInCampaign` until milestone 2.13.4.**

## File locations

The mod bundles definitions under `src/main/resources/data/`. Custom Minecraft data packs can override files with matching resource paths. These are **custom mod-managed data resources**, not built-in vanilla quest registries.

```text
src/main/resources/
└── data/
    └── theageofmachinery/
        ├── campaigns/
        │   └── main.json
        └── quests/
            ├── prototype_first_workshop.json
            └── prototype_first_tools.json

docs/
└── schemas/
    ├── campaign.schema.json
    └── quest.schema.json
```

Resource names are derived from `<namespace>:<relative path without .json>` within the respective `campaigns` or `quests` folder. The Java loader in 2.13.2–2.13.4 must verify the `id` inside each JSON document matches the resource ID derived from its filename. That prevents accidental save-breaking mismatches.

The single designated active campaign manifest for v1 is `theageofmachinery:main`; its `quests` array determines ordering, never file discovery order. Other quest resources may exist but are not automatically active.

In Minecraft 1.21.1 an **independent external data pack** requires its own `pack.mcmeta` with data pack `pack_format` **48**. The mod's `src/main/resources` already participates in Minecraft's mod resources and does not need a second `pack.mcmeta` in `data/`.

## Quest format

Required fields: `formatVersion` (must equal 1), `id`, `title`, `description`, `objectives`, `rewards`.

Every objective has a stable local `id`, an author-facing `description`, `requiredAmount` >=1, `optional` boolean, and a `trigger`. Supported triggers are:

- `MANUAL`: `{ "type": "MANUAL" }` (no target).
- `CRAFT_ITEM`: `{ "type": "CRAFT_ITEM", "item": "minecraft:crafting_table" }`.
- `PICKUP_ITEM`: `{ "type": "PICKUP_ITEM", "item": "minecraft:cobblestone" }`.
- `KINETIC_ACTIVE`: machine registry ID and positive `minSpeed`; `requiredAmount` is always 1. An optional `ingredients` checklist can describe the first-use machine build (added in 2.14.6).
- `MACHINE_PROCESS`: machine registry ID and output item registry ID; counts successful server-observed production.

As of 2.14.6, kinetic objectives may carry the same positive-quantity ingredient checklist as crafting objectives. Crafting still requires one; other objective types cannot declare ingredients. This is an additive format-v1 authoring change. The complete current schema is `docs/schemas/quest.schema.json` and is included in the workshop chapter ZIP. Ingredient quantities describe one recipe batch (e.g. a pair of Crushing Wheels), independently of the kinetic activation count of 1.

Crafting objectives in format v1 **require** an `ingredients` checklist with at least one ingredient. Each ingredient has positive `amount` and exactly one of `item` or `tag`, e.g. `{ "tag": "minecraft:planks", "amount": 4 }`. This is a *display checklist* and not a recipe override or proof of item ownership. Item tags make generic planks possible, though some real recipes have multiple alternatives that cannot yet be represented perfectly. `PICKUP_ITEM` retains existing **ground pickup** semantics, not all sources of acquisition.

For this prototype, `minecraft:planks` represents the four-plank Crafting Table requirement; the Furnace example uses eight Cobblestone as one valid recipe, not an exhaustive representation of all possible furnace crafting inputs.

Rewards have a stable local `id`, a `type`, and positive `amount`. Supported types: `EXPERIENCE`, or `ITEM` with an `item` registry ID. `rewards.main` is an array. `rewards.optional` maps optional objective IDs to reward arrays. Reward IDs must be unique **within each quest**, across the main and optional groups. The same local reward ID (e.g. `main_xp`) may occur in different quests, because rewards are claimed by `(questId, rewardId)`.

## Manifest format

`campaigns/main.json` has `formatVersion: 1`, `id: "theageofmachinery:main"` and a nonempty ordered array of quest IDs. Future quest definitions are held server-side, not sent to clients as a whole.

## Validation layers and migration guarantees

**JSON Schema** (`docs/schemas/`): validates JSON structure, required fields, integer ranges, recognized trigger/reward types, registry identifier *syntax*, and basic data constraints.

**Java parser / campaign validator** (milestones 2.13.2–2.13.3): must additionally check:

1. Resource file path and embedded `id` are identical for each resource.
2. Every manifest quest exists, no repeated quest IDs, and ordering is deterministic.
3. Every quest has **at least one mandatory objective**.
4. Objective IDs are unique within a quest.
5. Reward IDs are unique across a quest's main and optional rewards.
6. All `rewards.optional` keys name actual **optional** objectives.
7. Trigger/reward item IDs refer to valid registered items, and ingredient tags resolve appropriately with active data packs; registry ID syntax alone does not establish existence.
8. Existing saved campaign progress is compatible with definitions. Preserve IDs and quantities across the initial migration; reject unsafe live changes rather than silently rewriting progress.
9. Any failure rejects the candidate *as a whole*; never publish a partially registered campaign. Detailed errors identify resource and field.
10. Never reveal unreached quests, undisclosed rewards, or future ingredient checklists in network payloads. Keep manual advancement and optional reward calculation on the server.

## Reload policy

No JSON loading happens in 2.13.1. Plan to load on startup in 2.13.4 using NeoForge **1.21.1** `AddReloadListenerEvent` and Minecraft's server data-pack resources. Parsing may happen off-thread, but publication must obey the server thread lifecycle. Since `/reload` invokes loaders repeatedly, **2.13.5** will handle candidate validation and state compatibility before replacement, without resetting players. Do not assume `/reload` is safe to enable merely because startup parsing works.

## Test boundary for this milestone

The two example quests and manifest pass Draft 2020-12 JSON Schema validation. The authoring schema also rejects tested cases: zero objective amount; missing crafting ingredients; invalid registry ID syntax; negative reward amounts; duplicate quest IDs in manifest. A separate model-aware check confirms mandatory objectives, unique objective and reward IDs, and valid optional reward associations in the provided examples.

Schema tests are authoring-time checks only. Minecraft runtime parsing, data pack override precedence, world reloads and survival gameplay will be tested in later milestones.
