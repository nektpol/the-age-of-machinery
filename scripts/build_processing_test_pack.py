"""Build the complete, development-only 2.14.5 fixture using Python's standard library."""

import json
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "docs/tests/The_Age_of_Machinery_2.14.5_Test_Datapack.zip"


def build():
    files = {}

    def add_json(path, value):
        files[path] = json.dumps(value, indent=2) + "\n"

    add_json("pack.mcmeta", {"pack": {"pack_format": 48, "description": "TAM 2.14.5 processing tests - disposable Create+ world only"}})
    add_json("data/theageofmachinery/campaigns/main.json", {"formatVersion": 1, "id": "theageofmachinery:main", "quests": ["theageofmachinery:processing_test_2145"]})

    objectives = [{"id": "finish_testing", "description": "Operator-only test completion gate", "requiredAmount": 1, "optional": False, "trigger": {"type": "MANUAL"}}]
    objectives.append({"id": "power_mechanical_press", "description": "Operate a Mechanical Press at 16 RPM or more", "requiredAmount": 1, "optional": True, "trigger": {"type": "KINETIC_ACTIVE", "machine": "create:mechanical_press", "minSpeed": 16}})

    for machine, prefix in [("millstone", "mill"), ("crushing_wheel", "crush"), ("mechanical_mixer", "mix"), ("mechanical_press", "press")]:
        for output in (["iron_sheet", "redstone"] if prefix == "press" else ["flint", "redstone"]):
            item = "create:iron_sheet" if output == "iron_sheet" else f"minecraft:{output}"
            objectives.append({"id": f"{prefix}_{output}", "description": f"Observe {output} produced by {machine}", "requiredAmount": 1, "optional": True, "trigger": {"type": "MACHINE_PROCESS", "machine": f"create:{machine}", "output": item}})

    add_json("data/theageofmachinery/quests/processing_test_2145.json", {"formatVersion": 1, "id": "theageofmachinery:processing_test_2145", "title": "Processing Integration Laboratory", "description": "Development-only fixture. Keep this quest active throughout the acceptance tests.", "objectives": objectives, "rewards": {"main": [{"id": "main_xp", "type": "EXPERIENCE", "amount": 1}]}})

    for kind, primary, byproduct in [("milling", 2, 1), ("crushing", 3, 2), ("mixing", 4, 3), ("compacting", 1, 2)]:
        ingredients = [{"item": "minecraft:structure_void"}]
        if kind == "mixing":
            ingredients.append({"item": "minecraft:barrier"})
        if kind == "compacting":
            ingredients.append({"item": "minecraft:structure_void"})
        recipe = {"type": f"create:{kind}", "ingredients": ingredients, "results": [{"id": "create:iron_sheet" if kind == "compacting" else "minecraft:flint", "count": primary}, {"id": "minecraft:redstone", "count": byproduct}], "processing_time": 100}
        add_json(f"data/theageofmachinery/recipe/test_2145_{kind}.json", recipe)

    files["authoring/quest.schema.json"] = (ROOT / "docs/schemas/quest.schema.json").read_text(encoding="utf-8-sig")
    files["TEST_GUIDE.md"] = (ROOT / "docs/tests/2.14.5-processing.md").read_text(encoding="utf-8-sig")
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)

    with ZipFile(DESTINATION, "w", ZIP_DEFLATED) as archive:
        for path, content in sorted(files.items()):
            archive.writestr(path, content)

    print(f"Created {DESTINATION} ({len(files)} complete files)")


if __name__ == "__main__":
    build()
