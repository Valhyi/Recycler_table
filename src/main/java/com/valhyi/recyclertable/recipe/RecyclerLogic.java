package com.valhyi.recyclertable.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class RecyclerLogic {

    public static List<ItemStack> getUncraftIngredients(Level level, ItemStack inputStack) {
        List<ItemStack> resultIngredients = new ArrayList<>();

        if (inputStack.isEmpty() || level.isClientSide()) {
            return resultIngredients;
        }

        RecipeManager recipeManager = level.getRecipeManager();

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack recipeOutput = recipe.getResultItem(level.registryAccess());

            if (!recipeOutput.isEmpty() 
                    && ItemStack.isSameItem(recipeOutput, inputStack) 
                    && inputStack.getCount() >= recipeOutput.getCount()) {

                for (Ingredient ingredient : recipe.getIngredients()) {
                    ItemStack[] matchingStacks = ingredient.getItems();
                    if (matchingStacks.length > 0) {
                        resultIngredients.add(matchingStacks[0].copyWithCount(1));
                    }
                }
                break;
            }
        }

        return resultIngredients;
    }
}
