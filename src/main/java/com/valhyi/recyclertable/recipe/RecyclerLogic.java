package com.valhyi.recyclertable.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
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
import net.minecraft.world.item.crafting.StonecuttingRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class RecyclerLogic {

    // ES: Tipos de horno soportados, en el orden en que se prueban
    // EN: Supported furnace-like recipe types, in try order
    private static final RecipeType<?>[] COOKING_TYPES = {
            RecipeType.SMELTING,
            RecipeType.BLASTING,
            RecipeType.SMOKING,
            RecipeType.CAMPFIRE_COOKING
    };

    public static boolean canRecycle(ItemStack itemStack, Level level) {
        // ES: Cualquier item no vacío puede pasar por el reciclador; si no se
        //     encuentra receta, se devuelve intacto en processRecycling().
        // EN: Any non-empty item can go through the recycler; if no recipe is
        //     found it's returned unchanged in processRecycling().
        return !itemStack.isEmpty() && !level.isClientSide();
    }

    /**
     * ES: Punto de entrada principal. Busca los ingredientes que produjeron
     * este item probando, en orden de prioridad: tinte exacto -> Stonecutter
     * -> Crafting (shaped/shapeless/transmute) -> Hornos -> Herrería.
     * EN: Main entry point. Looks up the ingredients that produced this item,
     * trying in priority order: exact dye -> Stonecutter -> Crafting
     * (shaped/shapeless/transmute) -> Furnaces -> Smithing.
     */
    public static List<ItemStack> getRecipeIngredients(ItemStack inputStack, Level level) {
        List<ItemStack> ingredients = new ArrayList<>();

        if (inputStack.isEmpty() || level.isClientSide() || level.getServer() == null) {
            return ingredients;
        }

        RecipeManager recipeManager = level.getServer().getRecipeManager();
        HolderLookup.Provider registries = level.registryAccess();

        // ES: Caso especial - item teñido (cuero, arnés, etc.)
        // EN: Special case - dyed item (leather, harness, etc.)
        DyedItemColor dyedColor = inputStack.get(DataComponents.DYED_COLOR);
        if (dyedColor != null) {
            DyeColor exactDye = matchExactDyeColor(dyedColor.rgb());
            if (exactDye == null) {
                // ES: Mezcla de varios tintes o re-teñido: no reconstruible, no se recicla
                // EN: Mixed/re-dyed color: not reconstructible, don't recycle
                return ingredients;
            }

            ingredients.add(new ItemStack(DyeItem.byColor(exactDye)));

            ItemStack undyedCopy = inputStack.copyWithCount(1);
            undyedCopy.remove(DataComponents.DYED_COLOR);
            ingredients.addAll(findByPriority(undyedCopy, recipeManager, registries));
            return ingredients;
        }

        return findByPriority(inputStack, recipeManager, registries);
    }

    private static List<ItemStack> findByPriority(ItemStack target, RecipeManager recipeManager, HolderLookup.Provider registries) {
        List<ItemStack> found;

        found = findInStonecutter(target, recipeManager, registries);
        if (found != null) return found;

        found = findInCrafting(target, recipeManager, registries);
        if (found != null) return found;

        found = findInCooking(target, recipeManager, registries);
        if (found != null) return found;

        found = findInSmithing(target, recipeManager, registries);
        if (found != null) return found;

        return new ArrayList<>();
    }

    /**
     * ES: Devuelve el DyeColor cuyo color de textura coincide EXACTAMENTE con
     * el RGB dado, o null si es una mezcla que no corresponde a un solo tinte.
     * EN: Returns the DyeColor whose texture color EXACTLY matches the given
     * RGB, or null if it's a mix that doesn't match a single dye.
     */
    private static DyeColor matchExactDyeColor(int rgb) {
        for (DyeColor dye : DyeColor.values()) {
            if (dye.getTextureDiffuseColor() == rgb) {
                return dye;
            }
        }
        return null;
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
    private static List<ItemStack> findInStonecutter(ItemStack target, RecipeManager recipeManager, HolderLookup.Provider registries) {
        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.STONECUTTING)) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof StonecuttingRecipe stonecuttingRecipe)) continue;

            List<Ingredient> recipeIngredients = stonecuttingRecipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> samples = sampleFrom(recipeIngredients);
            if (samples.get(0).isEmpty()) continue;

            ItemStack output = stonecuttingRecipe.assemble(new SingleRecipeInput(samples.get(0)), registries);
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
    private static List<ItemStack> findInCrafting(ItemStack target, RecipeManager recipeManager, HolderLookup.Provider registries) {
        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.CRAFTING)) {
            Recipe<?> recipe = holder.value();

            // ES: Solo shaped/shapeless/transmute tienen un resultado fijo reconstruible.
            //     Se excluyen a propósito las recetas "special" (repairitem, firework,
            //     bookcloning, decorated_pot, etc.) y crafting_dye: su resultado depende
            //     de datos reales del input, no de una muestra genérica.
            // EN: Only shaped/shapeless/transmute have a fixed, reconstructible result.
            //     "Special" recipes and crafting_dye are intentionally excluded: their
            //     result depends on real input data, not a generic sample.
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
                output = shaped.assemble(input, registries);
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                output = shapeless.assemble(input, registries);
            } else {
                output = ((TransmuteRecipe) recipe).assemble(input, registries);
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
    private static List<ItemStack> findInCooking(ItemStack target, RecipeManager recipeManager, HolderLookup.Provider registries) {
        for (RecipeType<?> type : COOKING_TYPES) {
            for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(type)) {
                Recipe<?> recipe = holder.value();
                if (!(recipe instanceof AbstractCookingRecipe cookingRecipe)) continue;

                List<Ingredient> recipeIngredients = cookingRecipe.placementInfo().ingredients();
                if (recipeIngredients.isEmpty()) continue;

                List<ItemStack> samples = sampleFrom(recipeIngredients);
                if (samples.get(0).isEmpty()) continue;

                ItemStack output = cookingRecipe.assemble(new SingleRecipeInput(samples.get(0)), registries);
                if (!output.isEmpty() && output.getItem() == target.getItem()) {
                    List<ItemStack> result = new ArrayList<>();
                    ItemStack copy = samples.get(0).copy();
                    copy.setCount(1);
                    result.add(copy);
                    return result;
                }
            }
        }
        return null;
    }

    // ================= MESA DE HERRERÍA (solo smithing_transform) =================
    private static
