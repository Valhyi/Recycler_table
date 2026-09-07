package com.valhyi.recyclertable.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.*;

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

        // Si tiene receta, puede reciclarse
        return getRecipeIngredients(itemStack, level).size() > 0;
    }

    /**
     * Obtiene el resultado de una receta (ShapedRecipe o ShapelessRecipe)
     */
    private static ItemStack getRecipeResult(Object recipe) {
        try {
            if (recipe instanceof ShapedRecipe shapedRecipe) {
                return shapedRecipe.getResultItem().copy();
            } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                return shapelessRecipe.getResultItem().copy();
            }
        } catch (Exception e) {
            // Ignorar si falla
        }
        return ItemStack.EMPTY;
    }

    public static List<ItemStack> getRecipeIngredients(ItemStack inputStack, Level level) {
        List<ItemStack> ingredients = new ArrayList<>();

        if (inputStack.isEmpty() || level.isClientSide() || level.getServer() == null) {
            return ingredients;
        }

        var recipeManager = level.getServer().getRecipeManager();
        
        // Buscar la receta que produce exactamente este item
        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            var recipe = recipeHolder.value();
            
            // Obtener el resultado de la receta
            ItemStack recipeResult = getRecipeResult(recipe);
            
            // Verificar si el resultado coincide con el input (mismo item)
            if (!recipeResult.isEmpty() && recipeResult.getItem() == inputStack.getItem()) {
                // Receta encontrada! Extraer ingredientes
                if (recipe instanceof ShapedRecipe shapedRecipe) {
                    for (var ingredient : shapedRecipe.getIngredients()) {
                        if (!ingredient.isEmpty()) {
                            var firstItem = ingredient.getItems()[0];
                            if (!firstItem.isEmpty()) {
                                ItemStack copy = firstItem.copy();
                                copy.setCount(1);
                                ingredients.add(copy);
                            }
                        }
                    }
                    return ingredients;
                } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                    for (var ingredient : shapelessRecipe.getIngredients()) {
                        if (!ingredient.isEmpty()) {
                            var firstItem = ingredient.getItems()[0];
                            if (!firstItem.isEmpty()) {
                                ItemStack copy = firstItem.copy();
                                copy.setCount(1);
                                ingredients.add(copy);
                            }
                        }
                    }
                    return ingredients;
                }
            }
        }

        return ingredients;
    }

    /**
     * Procesa el reciclaje de un item
     * - Si está encantado: gasta 1 botella por encantamiento y retorna los libros de encantamientos separados
     * - Si tiene receta: retorna los ingredientes
     * - Si no tiene ni encantamientos ni receta: retorna el mismo item
     */
    public static List<ItemStack> processRecycling(ItemStack itemStack, ItemStack emptyBottle, ItemStack book, Level level) {
        List<ItemStack> results = new ArrayList<>();

        if (itemStack.isEmpty() || level.isClientSide()) {
            return results;
        }

        // CASO 1: Item encantado - Separar en libros individuales
        ItemEnchantments enchantments = itemStack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            // Gastar 1 botella por cada encantamiento
            if (emptyBottle.getCount() >= enchantments.size()) {
                emptyBottle.shrink(enchantments.size());
                
                // Crear 1 libro por encantamiento
                for (var enchantmentEntry : enchantments.entrySet()) {
                    ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
                    ItemEnchantments.Mutable mutableEnchantments = new ItemEnchantments.Mutable(new ItemEnchantments());
                    mutableEnchantments.set(enchantmentEntry.getKey(), enchantmentEntry.getValue());
                    enchantedBook.set(DataComponents.ENCHANTMENTS, mutableEnchantments.toImmutable());
                    results.add(enchantedBook);
                }
                return results;
            } else {
                // No hay suficientes botellas - no reciclar
                return results;
            }
        }

        // CASO 2: Item con receta - Retornar ingredientes
        List<ItemStack> ingredients = getRecipeIngredients(itemStack, level);
        if (!ingredients.isEmpty()) {
            results.addAll(ingredients);
            return results;
        }

        // CASO 3: Sin receta ni encantamientos - Retornar el mismo item
        ItemStack copy = itemStack.copy();
        copy.setCount(1);
        results.add(copy);
        return results;
    }
}
