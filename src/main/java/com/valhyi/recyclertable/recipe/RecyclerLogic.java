package com.valhyi.recyclertable.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class RecyclerLogic {

    public static boolean canRecycle(ItemStack itemStack, Level level) {
        if (itemStack.isEmpty() || level.isClientSide()) {
            return false;
        }

        // Un item puede reciclarse si:
        // 1. Tiene encantamientos, O
        // 2. Tiene una receta conocida
        
        ItemEnchantments enchantments = itemStack.get(DataComponents.ENCHANTMENTS);
        boolean isEnchanted = enchantments != null && !enchantments.isEmpty();
        
        if (isEnchanted) {
            return true;
        }

        // Si no tiene encantamientos, verificar si tiene receta
        return hasRecipe(itemStack, level);
    }

    public static boolean hasRecipe(ItemStack itemStack, Level level) {
        if (level.isClientSide() || level.getServer() == null) {
            return false;
        }
        
        // Buscar en el RecipeManager del servidor
        var recipeManager = level.getServer().getRecipeManager();
        
        // Buscar receta de crafteo para este item
        for (RecipeHolder<?> recipe : recipeManager.getRecipes()) {
            if (recipe.value().getResultItem().is(itemStack.getItem())) {
                // Verificar que sea una receta válida (Shaped o Shapeless)
                if (recipe.value() instanceof ShapedRecipe || recipe.value() instanceof ShapelessRecipe) {
                    return true;
                }
            }
        }
        
        return false;
    }

    public static List<ItemStack> getRecipeIngredients(ItemStack inputStack, Level level) {
        List<ItemStack> ingredients = new ArrayList<>();

        if (inputStack.isEmpty() || level.isClientSide() || level.getServer() == null) {
            return ingredients;
        }

        var recipeManager = level.getServer().getRecipeManager();
        
        // Buscar la receta para este item
        for (RecipeHolder<?> recipe : recipeManager.getRecipes()) {
            if (recipe.value().getResultItem().is(inputStack.getItem())) {
                // Si es una receta válida (Shaped o Shapeless)
                if (recipe.value() instanceof ShapedRecipe shapedRecipe) {
                    // Obtener ingredientes de la receta
                    for (var ingredient : shapedRecipe.getIngredients()) {
                        ItemStack[] items = ingredient.getItems();
                        if (items.length > 0) {
                            ingredients.add(items[0].copy());
                        }
                    }
                    break;
                } else if (recipe.value() instanceof ShapelessRecipe shapelessRecipe) {
                    // Obtener ingredientes de la receta
                    for (var ingredient : shapelessRecipe.getIngredients()) {
                        ItemStack[] items = ingredient.getItems();
                        if (items.length > 0) {
                            ingredients.add(items[0].copy());
                        }
                    }
                    break;
                }
            }
        }
        
        return ingredients;
    }

    public static List<ItemStack> processRecycling(ItemStack inputStack, ItemStack emptyBottle, ItemStack book, Level level) {
        List<ItemStack> results = new ArrayList<>();

        if (!canRecycle(inputStack, level)) {
            // Si no puede reciclarse, NO devolver nada (rechazarlo)
            return results;
        }

        List<ItemStack> ingredients = getRecipeIngredients(inputStack, level);
        
        // Obtener encantamientos - Minecraft 26.2
        ItemEnchantments enchantments = inputStack.get(DataComponents.ENCHANTMENTS);
        boolean isEnchanted = enchantments != null && !enchantments.isEmpty();
        boolean hasEmptyBottle = !emptyBottle.isEmpty();
        boolean hasBook = !book.isEmpty();

        // Si está encantado
        if (isEnchanted) {
            // Validar que tenga los recursos necesarios (botella y libro)
            if (!hasEmptyBottle || !hasBook) {
                // No puede procesar sin recursos, enviar item original al output
                results.add(inputStack.copy());
                return results;
            }

            // Devolver ingredientes del item
            results.addAll(ingredients);

            // Crear libro con encantamientos (Cut & Paste de TODOS los encantamientos)
            ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
            copyAllEnchantments(enchantedBook, enchantments);
            results.add(enchantedBook);

            // Crear botella de XP
            ItemStack xpBottle = new ItemStack(Items.EXPERIENCE_BOTTLE);
            results.add(xpBottle);
        } else {
            // Sin encantamientos, solo devolver ingredientes
            results.addAll(ingredients);
        }

        return results;
    }

    /**
     * Copia TODOS los encantamientos al libro (Cut & Paste completo)
     * Mantiene los niveles de encantamiento exactamente igual: Sharpness V -> Sharpness V
     */
    private static void copyAllEnchantments(ItemStack target, ItemEnchantments sourceEnchantments) {
        if (sourceEnchantments != null && !sourceEnchantments.isEmpty()) {
            // Crear una copia mutable de los encantamientos
            ItemEnchantments.Mutable mutableEnchantments = new ItemEnchantments.Mutable(sourceEnchantments);
            // Aplicar los encantamientos al libro
            target.set(DataComponents.ENCHANTMENTS, mutableEnchantments.toImmutable());
        }
    }
}
