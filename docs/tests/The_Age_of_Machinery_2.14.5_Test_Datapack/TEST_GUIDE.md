# 2.14.5 — Simple Create+ processing test

**Passed — 28 October 2026.** The user confirmed progress on every test objective in the full Create+ pack. This completes the basic 2.14.5 integration test. No repeat testing or 4096-item production is required.

## Short guide for future runs

1. Enable the test ZIP when creating a fresh creative world with cheats. Do not replace the pack in the existing accepted test world.
2. Place and power the machines yourself. Use 64 RPM or more for the Mixer. For Mixer and Press basin recipes, arrange: **machine, one air block, Basin**.
3. Get only the ingredients needed:

```mcfunction
/give @s minecraft:structure_void 5
/give @s minecraft:barrier 1
```

4. Run each recipe once:

| Machine | Put in | Produces |
|---|---|---|
| Millstone | 1 Structure Void | 2 Flint + 1 Redstone |
| Crushing Wheels | 1 Structure Void | 3 Flint + 2 Redstone |
| Mixer with Basin | 1 Structure Void + 1 Barrier | 4 Flint + 3 Redstone |
| Press with Basin | 2 Structure Void | 1 Iron Sheet + 2 Redstone |

Drop items with **Q** into the machine/Basin; do not place Structure Void blocks. No heat is needed. Empty the Basins and clear their filters before testing.

5. Run `/tamruntime player`. Progress on each machine's matching objectives means the basic integration test passes. The revised fixture targets **1 item per objective**; no repeated production is needed. Leave the manual `finish_testing` gate alone unless you want to end the test quest.

These are custom recipes supplied by the test ZIP. The existing save can keep its old 4096 targets: the observed progress already passed, so there is no need to replace its pack or fill those counters.

## Recorded checks

- Build and processing replay/byproduct regression checks: passed.
- Eclipse/JDT null analysis: zero warnings or errors after the null-warning fix.
- Full Create+ startup and fixture schema/archive validation: passed.
- Progress on every test objective: user confirmed, accepted for 2.14.5.

Broader automation, exact batch counts, restart and multiplayer checks belong to 2.14.7; they are not claimed by this basic progress test. Next milestone: **2.14.6 — Create campaign content**.
