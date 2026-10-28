"""Package the native JSON survival chapter, full schema and short acceptance guide."""

from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "datapacks/tam_create_workshop"
DESTINATION = ROOT / "docs/datapacks/The_Age_of_Machinery_2.14.6_Create_Workshop.zip"

DESTINATION.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(DESTINATION, "w", ZIP_DEFLATED) as archive:
    for source in sorted(SOURCE.rglob("*")):
        if source.is_file():
            archive.write(source, source.relative_to(SOURCE).as_posix())
    archive.write(ROOT / "docs/schemas/quest.schema.json", "authoring/quest.schema.json")
    archive.write(ROOT / "docs/tests/2.14.6-create-chapter.md", "TEST_GUIDE.md")

print(f"Created complete chapter pack: {DESTINATION}")
