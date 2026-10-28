"""Authoring checks; requires jsonschema. Does not claim Minecraft gameplay coverage."""

import json
from pathlib import Path
from zipfile import ZipFile

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]
schema = json.loads((ROOT / "docs/schemas/quest.schema.json").read_text(encoding="utf-8-sig"))
manifest_schema = json.loads((ROOT / "docs/schemas/campaign.schema.json").read_text(encoding="utf-8-sig"))
Draft202012Validator.check_schema(schema)

with ZipFile(ROOT / "docs/tests/The_Age_of_Machinery_2.14.5_Test_Datapack.zip") as archive:
    assert archive.testzip() is None
    assert len(archive.namelist()) == 9
    assert json.loads(archive.read("authoring/quest.schema.json")) == schema
    manifest = json.loads(archive.read("data/theageofmachinery/campaigns/main.json"))
    Draft202012Validator(manifest_schema).validate(manifest)
    assert manifest["quests"] == ["theageofmachinery:processing_test_2145"]
    quest = json.loads(archive.read("data/theageofmachinery/quests/processing_test_2145.json"))
    Draft202012Validator(schema).validate(quest)
    assert quest["id"] == manifest["quests"][0]
    assert len({objective["id"] for objective in quest["objectives"]}) == len(quest["objectives"])
    assert all(objective["requiredAmount"] == 1 for objective in quest["objectives"])
    for kind, primary, byproduct in [("milling", 2, 1), ("crushing", 3, 2), ("mixing", 4, 3), ("compacting", 1, 2)]:
        recipe = json.loads(archive.read(f"data/theageofmachinery/recipe/test_2145_{kind}.json"))
        assert recipe["type"] == f"create:{kind}"
        assert [result["count"] for result in recipe["results"]] == [primary, byproduct]
        assert all("chance" not in result for result in recipe["results"])
    assert json.loads(archive.read("pack.mcmeta"))["pack"]["pack_format"] == 48

print("PASS: complete ZIP integrity, full schema copy, quest/manifest schema, unique objectives and deterministic recipe quantities.")
