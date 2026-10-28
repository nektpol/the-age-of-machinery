package com.TheAgeOfMachinery.create;

import java.util.UUID;

// Runs with the JDK alone; no Minecraft bootstrap or additional testing dependency.
public final class ProcessingOperationLedgerRegression
{
    public static void main(String[] arguments)
    {
        UUID installation = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        ProcessingOperationLedger ledger = new ProcessingOperationLedger(16);

        require(ledger.remember(installation, operation, "minecraft:sand"), "First output was lost.");
        require(ledger.remember(installation, operation, "minecraft:flint"), "Byproduct from the same operation was lost.");
        require(!ledger.remember(installation, operation, "minecraft:sand"), "Replayed primary output was credited twice.");
        require(!ledger.remember(installation, operation, "minecraft:flint"), "Replayed byproduct was credited twice.");
        require(ledger.remember(installation, UUID.randomUUID(), "minecraft:sand"), "A second real cycle was rejected.");
        require(ledger.remember(UUID.randomUUID(), operation, "minecraft:sand"), "Separate installation was rejected.");
        require(new ProcessingOperationLedger(16).remember(installation, operation, "minecraft:sand"), "Separate server ledger was contaminated.");

        ProcessingOperationLedger bounded = new ProcessingOperationLedger(2);
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        bounded.remember(installation, operation, "minecraft:sand");
        bounded.remember(installation, second, "minecraft:sand");
        require(!bounded.remember(installation, operation, "minecraft:sand"), "Replay was accepted before eviction.");
        bounded.remember(installation, third, "minecraft:sand");
        require(!bounded.remember(installation, second, "minecraft:sand"), "Recent operation was evicted.");
        require(bounded.remember(installation, operation, "minecraft:sand"), "Oldest entry was not evicted at the bound.");

        try
        {
            new ProcessingOperationLedger(0);
            throw new AssertionError("Zero ledger capacity was accepted.");
        }
        catch (IllegalArgumentException expected)
        {
            // Expected validation.
        }

        System.out.println("Processing operation regressions passed: byproducts, replay, cycles, installations, server isolation and bounded eviction.");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
