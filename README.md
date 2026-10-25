# The Age of Machinery

A Create-only quest and progression mod for Minecraft 1.21.1 (NeoForge).

## Status

Early development: the mod builds and loads. Quest functionality is planned.

## Goals

- Engineering challenges that encourage discovery rather than spoil solutions.
- Three optional hint levels.
- Ingredient checklists with quantities when introducing new crafting goals.
- Automatic objective tracking where supported.
- **Individual quest progression per player**, including multiplayer.
- Persistent progress across sessions.
- Data-driven quest definitions in JSON.

## Development stack

- Minecraft 1.21.1
- NeoForge 21.1.233 (initial compatibility target)
- Java 21
- Gradle / ModDevGradle
- Visual Studio Code
- Create integration planned

## Building

Install JDK 21, then run from the project root:

```powershell
.\gradlew.bat build
```

The JAR will be generated under `build/libs/`.

To start the isolated development client:

```powershell
.\gradlew.bat runClient
```

The development client does not automatically load your Modrinth profile mods or saves.

## Testing in Modrinth

Back up your world first. Copy the built JAR into the compatible NeoForge profile `mods/` directory, replacing only previous versions of this mod. Create and its dependencies must be installed separately.

## Planned architecture

- `QuestDefinition`: immutable quest metadata, prerequisites, objectives, hints, ingredients.
- `QuestProgress`: per-player mutable objective and completion state.
- `QuestManager`: progression and unlocking.
- Objective evaluators: crafting, placement, processing, automation.
- Create integration: gameplay event and machinery detection.
- Persistence: server-authoritative per-player data.
- Quest journal: objectives, hints, and ingredient requirements.

Quest IDs use namespaced identifiers such as `theageofmachinery:first_settlement`.

## Roadmap

- [x] Generate NeoForge project and verify build.
- [x] Load mod in Modrinth Create+ profile.
- [ ] Implement quest definitions and validation.
- [ ] Implement per-player progress and persistence.
- [ ] Add Create development dependency.
- [ ] Implement journal and progressive hints.
- [ ] Build introductory quest chain.
- [ ] Detect functional Create automation.

## License

Currently All Rights Reserved, as declared by the generated project. No open-source license is granted at this time.
