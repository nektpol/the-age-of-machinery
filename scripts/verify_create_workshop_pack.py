"""Validate chapter JSON, single-recipe quantities and the complete deliverable.

Requires jsonschema and the installed full Create+ mods directory. These authoring
checks do not claim that an in-world playthrough has run.
"""

from collections import Counter
from copy import deepcopy
import json
from pathlib import Path
from zipfile import ZipFile

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "datapacks/tam_create_workshop"
schema = json.loads((ROOT / "docs/schemas/quest.schema.json").read_text(encoding="utf-8-sig"))
manifest_schema = json.loads((ROOT / "docs/schemas/campaign.schema.json").read_text(encoding="utf-8-sig"))
Draft202012Validator.check_schema(schema)
validator = Draft202012Validator(schema)
manifest = json.loads((SOURCE / "data/theageofmachinery/campaigns/main.json").read_text())
Draft202012Validator(manifest_schema).validate(manifest)
assert len(manifest["quests"]) == 8

recipes = {}
with ZipFile(ROOT / "run-createplus/mods/create-1.21.1-6.0.10.jar") as archive:
    for name in archive.namelist():
        if name.startswith("data/create/recipe/") and name.endswith(".json"):
            recipes[name] = json.loads(archive.read(name))


def input_counts(recipe):
    def key(ingredient):
        return "#" + ingredient["tag"] if "tag" in ingredient else ingredient["item"]
    if "pattern" in recipe:
        symbols = Counter("".join(recipe["pattern"]).replace(" ", ""))
        return Counter({key(recipe["key"][symbol]): count for symbol, count in symbols.items()})
    return Counter(key(ingredient) for ingredient in recipe["ingredients"])


checked_recipe_paths = set()
definitions = []
for quest_id in manifest["quests"]:
    name = quest_id.split(":", 1)[1]
    quest = json.loads((SOURCE / f"data/theageofmachinery/quests/{name}.json").read_text())
    validator.validate(quest)
    assert quest["id"] == quest_id
    assert any(not objective["optional"] for objective in quest["objectives"])
    objective_ids = {objective["id"] for objective in quest["objectives"]}
    assert len(objective_ids) == len(quest["objectives"])
    optional_ids = {objective["id"] for objective in quest["objectives"] if objective["optional"]}
    assert set(quest["rewards"].get("optional", {})) <= optional_ids
    reward_ids = [reward["id"] for reward in quest["rewards"]["main"]]
    reward_ids += [reward["id"] for group in quest["rewards"].get("optional", {}).values() for reward in group]
    assert len(reward_ids) == len(set(reward_ids))
    if name.startswith("prototype_"):
        original = ROOT / f"src/main/resources/data/theageofmachinery/quests/{name}.json"
        assert original.read_bytes() == (SOURCE / f"data/theageofmachinery/quests/{name}.json").read_bytes()
        continue
    definitions.append(quest)
    for objective in quest["objectives"]:
        trigger = objective["trigger"]
        if trigger["type"] == "CRAFT_ITEM":
            matching = [(path, recipe) for path, recipe in recipes.items() if recipe["type"].startswith("minecraft:crafting_") and recipe.get("result", {}).get("id") == trigger["item"]]
            checklist = Counter({("#" + entry["tag"] if "tag" in entry else entry["item"]): entry["amount"] for entry in objective["ingredients"]})
            exact = [(path, recipe) for path, recipe in matching if input_counts(recipe) == checklist and recipe["result"].get("count", 1) == objective["requiredAmount"]]
            assert exact, (quest_id, objective["id"], "Checklist does not match a single actual crafting recipe")
            checked_recipe_paths.add(exact[0][0])
        elif trigger["type"] == "MACHINE_PROCESS":
            kinds = {"create:mechanical_press": {"create:pressing", "create:compacting"}, "create:millstone": {"create:milling"}, "create:mechanical_mixer": {"create:mixing"}, "create:crushing_wheel": {"create:crushing", "create:milling"}}[trigger["machine"]]
            matching = [path for path, recipe in recipes.items() if recipe["type"] in kinds and any(result.get("id") == trigger["output"] and result.get("chance", 1) == 1 for result in recipe.get("results", []))]
            assert matching, (quest_id, objective["id"], "No guaranteed result in the actual recipe set")
            checked_recipe_paths.update(matching)

wheel_recipe = recipes["data/create/recipe/mechanical_crafting/crushing_wheel.json"]
wheel_checklist = definitions[-1]["objectives"][1]["ingredients"]
assert input_counts(wheel_recipe) == Counter({("#" + entry["tag"] if "tag" in entry else entry["item"]): entry["amount"] for entry in wheel_checklist})
checked_recipe_paths.add("data/create/recipe/mechanical_crafting/crushing_wheel.json")

# Check other installed addons for replacements of the recipes used by this chapter.
for jar in (ROOT / "run-createplus/mods").glob("*.jar"):
    if jar.name == "create-1.21.1-6.0.10.jar":
        continue
    with ZipFile(jar) as archive:
        assert not checked_recipe_paths.intersection(archive.namelist()), f"Review recipe overrides in {jar.name}"

negative = deepcopy(definitions[-1])
negative["objectives"][1]["ingredients"][0]["amount"] = 0
assert list(validator.iter_errors(negative)), "Zero checklist amount was accepted"
negative = deepcopy(definitions[0])
del negative["objectives"][0]["ingredients"]
assert list(validator.iter_errors(negative)), "Missing crafting checklist was accepted"

with ZipFile(ROOT / "docs/datapacks/The_Age_of_Machinery_2.14.6_Create_Workshop.zip") as archive:
    assert archive.testzip() is None
    assert json.loads(archive.read("authoring/quest.schema.json")) == schema
    assert "TEST_GUIDE.md" in archive.namelist()
    assert not any("/recipe/" in path for path in archive.namelist()), "Survival chapter must not add test recipes"
    for source in SOURCE.rglob("*"):
        if source.is_file():
            assert archive.read(source.relative_to(SOURCE).as_posix()) == source.read_bytes()

print("PASS: 8 ordered quests, complete ZIP/schema, original prelude, one-craft checklists, normal Create+ recipes, optional rewards and negative authoring checks.")
