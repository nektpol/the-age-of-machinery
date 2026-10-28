"""Build a complete two-item-per-objective fixture for the 2.14.7 restart test."""

import json
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "docs/tests/The_Age_of_Machinery_2.14.7_Integration_Test.zip"
QUEST_ID = "theageofmachinery:integration_test_2147"

quest = {
    "formatVersion": 1,
    "id": QUEST_ID,
    "title": "Integration Check",
    "description": "Development fixture: verify machine ownership and progress across a world restart. Not campaign content.",
    "objectives": [
        {
            "id": "press_sheets",
            "description": "Produce 2 Iron Sheets with your registered Press",
            "requiredAmount": 2,
            "optional": False,
            "trigger": {"type": "MACHINE_PROCESS", "machine": "create:mechanical_press", "output": "create:iron_sheet"},
        },
        {
            "id": "mix_alloy",
            "description": "Produce 2 Andesite Alloy with your registered Mixer before advancing",
            "requiredAmount": 2,
            "optional": True,
            "trigger": {"type": "MACHINE_PROCESS", "machine": "create:mechanical_mixer", "output": "create:andesite_alloy"},
        },
    ],
    "rewards": {
        "main": [
            {"id": "main_xp", "type": "EXPERIENCE", "amount": 10},
            {"id": "main_iron", "type": "ITEM", "item": "minecraft:iron_ingot", "amount": 1},
        ],
        "optional": {"mix_alloy": [{"id": "bonus_mixing", "type": "EXPERIENCE", "amount": 5}]},
    },
}

documents = {
    "pack.mcmeta": {"pack": {"pack_format": 48, "description": "TAM 2.14.7 ownership and restart test - two items per objective"}},
    "data/theageofmachinery/campaigns/main.json": {"formatVersion": 1, "id": "theageofmachinery:main", "quests": [QUEST_ID]},
    "data/theageofmachinery/quests/integration_test_2147.json": quest,
}

DESTINATION.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(DESTINATION, "w", ZIP_DEFLATED) as archive:
    for name, document in documents.items():
        archive.writestr(name, json.dumps(document, indent=2) + "\n")
    archive.write(ROOT / "docs/schemas/quest.schema.json", "authoring/quest.schema.json")
    archive.write(ROOT / "docs/tests/2.14.7-integration.md", "TEST_GUIDE.md")

print(f"Created complete integration fixture: {DESTINATION}")
