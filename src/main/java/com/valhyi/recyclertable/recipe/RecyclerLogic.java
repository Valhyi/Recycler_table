package com.valhyi.recyclertable.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class RecyclerLogic {

    public static boolean canRecycle(ItemStack itemStack, Level level) {
        return !itemStack.isEmpty() && !level.isClientSide();
    }

    /**
     * ES: Punto de entrada principal. Busca los ingredientes que produjeron
     * este item probando, en orden de prioridad: tinte exacto -> Stonecutter
     * -> Crafting (shaped/shapeless/transmute) -> Hornos -> Herrería.
     */
    public static List<ItemStack> getRecipeIngredients(ItemStack inputStack, Level level) {
        List<ItemStack> ingredients = new ArrayList<>();

        if (inputStack.isEmpty() || level.isClientSide() || level.getServer() == null) {
            return ingredients;
        }

        RecipeManager recipeManager = level.getServer().getRecipeManager();

        DyedItemColor dyedColor = inputStack.get(DataComponents.DYED_COLOR);
        if (dyedColor != null) {
            DyeColor exactDye = matchExactDyeColor(dyedColor.rgb());
            if (exactDye == null) {
                // ES: Mezcla de varios tintes o re-teñido: no reconstruible
                return ingredients;
            }

            ingredients.add(dyeItemStack(exactDye));

            ItemStack undyedCopy = inputStack.copyWithCount(1);
            undyedCopy.remove(DataComponents.DYED_COLOR);
            ingredients.addAll(findByPriority(undyedCopy, recipeManager));
            return ingredients;
        }

        return findByPriority(inputStack, recipeManager);
    }

    private static List<ItemStack> findByPriority(ItemStack target, RecipeManager recipeManager) {
        List<ItemStack> found;

        found = findInStonecutter(target, recipeManager);
        if (found != null) return found;

        found = findInCrafting(target, recipeManager);
        if (found != null) return found;

        found = findInCooking(target, recipeManager);
        if (found != null) return found;

        found = findInSmithing(target, recipeManager);
        if (found != null) return found;

        return new ArrayList<>();
    }

    private static DyeColor matchExactDyeColor(int rgb) {
        for (DyeColor dye : DyeColor.values()) {
            if (dye.getTextureDiffuseColor() == rgb) {
                return dye;
            }
        }
        return null;
    }

    /**
     * ES: Devuelve el ItemStack del tinte vanilla correspondiente a un DyeColor.
     * EN: Returns the vanilla dye ItemStack for a given DyeColor.
     */
    private static ItemStack dyeItemStack(DyeColor color) {
        return switch (color) {
            case WHITE -> new ItemStack(Items.WHITE_DYE);
            case ORANGE -> new ItemStack(Items.ORANGE_DYE);
            case MAGENTA -> new ItemStack(Items.MAGENTA_DYE);
            case LIGHT_BLUE -> new ItemStack(Items.LIGHT_BLUE_DYE);
            case YELLOW -> new ItemStack(Items.YELLOW_DYE);
            case LIME -> new ItemStack(Items.LIME_DYE);
            case PINK -> new ItemStack(Items.PINK_DYE);
            case GRAY -> new ItemStack(Items.GRAY_DYE);
            case LIGHT_GRAY -> new ItemStack(Items.LIGHT_GRAY_DYE);
            case CYAN -> new ItemStack(Items.CYAN_DYE);
            case PURPLE -> new ItemStack(Items.PURPLE_DYE);
            case BLUE -> new ItemStack(Items.BLUE_DYE);
            case BROWN -> new ItemStack(Items.BROWN_DYE);
            case GREEN -> new ItemStack(Items.GREEN_DYE);
            case RED -> new ItemStack(Items.RED_DYE);
            case BLACK -> new ItemStack(Items.BLACK_DYE);
        };
    }

    private static List<ItemStack> sampleFrom(List<Ingredient> ingredients) {
        List<ItemStack> samples = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            var first = ingredient.items().findFirst();
            samples.add(first.isPresent() ? new ItemStack(first.get().value()) : ItemStack.EMPTY);
        }
        return samples;
    }

    // ================= STONECUTTER =================
    private static List<ItemStack> findInStonecutter(ItemStack target, RecipeManager recipeManager) {
        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.STONECUTTING)) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof StonecutterRecipe stonecutterRecipe)) continue;

            List<Ingredient> recipeIngredients = stonecutterRecipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> samples = sampleFrom(recipeIngredients);
            if (samples.get(0).isEmpty()) continue;

            ItemStack output = stonecutterRecipe.assemble(new SingleRecipeInput(samples.get(0)));
            if (!output.isEmpty() && output.getItem() == target.getItem()) {
                List<ItemStack> result = new ArrayList<>();
                ItemStack copy = samples.get(0).copy();
                copy.setCount(1);
                result.add(copy);
                return result;
            }
        }
        return null;
    }

    // ================= CRAFTING (shaped / shapeless / transmute, cubre 2x2 y 3x3) =================
    private static List<ItemStack> findInCrafting(ItemStack target, RecipeManager recipeManager) {
        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.CRAFTING)) {
            Recipe<?> recipe = holder.value();

            if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe) && !(recipe instanceof TransmuteRecipe)) {
                continue;
            }

            List<Ingredient> recipeIngredients = recipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> samples = sampleFrom(recipeIngredients);
            if (samples.stream().anyMatch(ItemStack::isEmpty)) continue;

            CraftingInput input = CraftingInput.of(samples.size(), 1, samples);

            ItemStack output;
            if (recipe instanceof ShapedRecipe shaped) {
                output = shaped.assemble(input);
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                output = shapeless.assemble(input);
            } else {
                output = ((TransmuteRecipe) recipe).assemble(input);
            }

            if (!output.isEmpty() && output.getItem() == target.getItem()) {
                List<ItemStack> result = new ArrayList<>();
                for (ItemStack sample : samples) {
                    ItemStack copy = sample.copy();
                    copy.setCount(1);
                    result.add(copy);
                }
                return result;
            }
        }
        return null;
    }

    // ================= HORNOS (smelting / blasting / smoking / campfire) =================
    private static List<ItemStack> findInCooking(ItemStack target, RecipeManager recipeManager) {
        List<ItemStack> result;

        result = searchCookingType(RecipeType.SMELTING, target, recipeManager);
        if (result != null) return result;

        result = searchCookingType(RecipeType.BLASTING, target, recipeManager);
        if (result != null) return result;

        result = searchCookingType(RecipeType.SMOKING, target, recipeManager);
        if (result != null) return result;

        result = searchCookingType(RecipeType.CAMPFIRE_COOKING, target, recipeManager);
        return result;
    }

    private static <T extends AbstractCookingRecipe> List<ItemStack> searchCookingType(RecipeType<T> type, ItemStack target, RecipeManager recipeManager) {
        for (RecipeHolder<T> holder : recipeManager.recipeMap().byType(type)) {
            T recipe = holder.value();

            List<Ingredient> recipeIngredients = recipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> samples = sampleFrom(recipeIngredients);
            if (samples.get(0).isEmpty()) continue;

            ItemStack output = recipe.assemble(new SingleRecipeInput(samples.get(0)));
            if (!output.isEmpty() && output.getItem() == target.getItem()) {
                List<ItemStack> result = new ArrayList<>();
                ItemStack copy = samples.get(0).copy();
                copy.setCount(1);
                result.add(copy);
                return result;
            }
        }
        return null;
    }

    // ================= MESA DE HERRERÍA (solo smithing_transform) =================
    private static List<ItemStack> findInSmithing(ItemStack target, RecipeManager recipeManager) {
        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.SMITHING)) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof SmithingTransformRecipe smithingRecipe)) continue;

            List<Ingredient> recipeIngredients = smithingRecipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> samples = sampleFrom(recipeIngredients);
            ItemStack template = samples.size() > 0 ? samples.get(0) : ItemStack.EMPTY;
            ItemStack base = samples.size() > 1 ? samples.get(1) : ItemStack.EMPTY;
            ItemStack addition = samples.size() > 2 ? samples.get(2) : ItemStack.EMPTY;
            if (base.isEmpty()) continue;

            ItemStack output = smithingRecipe.assemble(new SmithingRecipeInput(template, base, addition));
            if (!output.isEmpty() && output.getItem() == target.getItem()) {
                List<ItemStack> result = new ArrayList<>();
                for (ItemStack sample : samples) {
                    if (sample.isEmpty()) continue;
                    ItemStack copy = sample.copy();
                    copy.setCount(1);
                    result.add(copy);
                }
                return result;
            }
        }
        return null;
    }

    public static List<ItemStack> processRecycling(ItemStack inputStack, ItemStack emptyBottle, ItemStack book, Level level) {
        List<ItemStack> results = new ArrayList<>();

        if (inputStack.isEmpty() || level == null) {
            return results;
        }

        ItemEnchantments enchantments = inputStack.get(DataComponents.ENCHANTMENTS);
        boolean isEnchanted = enchantments != null && !enchantments.isEmpty();
        boolean hasEmptyBottle = !emptyBottle.isEmpty();
        boolean hasBook = !book.isEmpty();

        List<ItemStack> ingredients = getRecipeIngredients(inputStack, level);

        if (isEnchanted) {
            if (!hasEmptyBottle || !hasBook) {
                results.add(inputStack.copy());
                return results;
            }

            if (!ingredients.isEmpty()) {
                results.addAll(ingredients);
            }

            createSingleEnchantmentBooks(enchantments, results, level);

            ItemStack xpBottle = new ItemStack(Items.EXPERIENCE_BOTTLE);
            results.add(xpBottle);
        } else {
            if (!ingredients.isEmpty()) {
                results.addAll(ingredients);
            } else {
                results.add(inputStack.copy());
            }
        }

        return results;
    }

    private static void createSingleEnchantmentBooks(ItemEnchantments sourceEnchantments, List<ItemStack> results, Level level) {
        if (sourceEnchantments != null && !sourceEnchantments.isEmpty()) {
            for (var entry : sourceEnchantments.entrySet()) {
                var enchantment = entry.getKey();
                int level_value = entry.getIntValue();

                ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);

                ItemEnchantments.Mutable mutableEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mutableEnchantments.set(enchantment, level_value);

                enchantedBook.set(DataComponents.ENCHANTMENTS, mutableEnchantments.toImmutable());
                results.add(enchantedBook);
            }
        }
    }
}
