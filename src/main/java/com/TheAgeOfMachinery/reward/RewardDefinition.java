package com.TheAgeOfMachinery.reward;

public sealed interface RewardDefinition permits ExperienceReward, ItemReward 
{
    String id();
    int amount();
}
