package com.valhyi.recyclertable.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
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
     * Obtiene los ingredientes de CUALQUIER tipo de receta que produzca este item
     * Soporta: ShapedRecipe, ShapelessRecipe, y otras recetas customizadas
     */
    public static List<ItemStack> getRecipeIngredients(ItemStack inputStack, Level level) {
        List<ItemStack> ingredients = new ArrayList<>();

        if (inputStack.isEmpty() || level.isClientSide() || level.getServer() == null) {
            return ingredients;
        }

        try {
            var recipeManager = level.getServer().getRecipeManager();
            
            // Buscar la receta que produce exactamente este item
            for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
                var recipe = recipeHolder.value();
                ItemStack recipeResult = recipe.getResultItem().copy();
                
                // Verificar si el resultado coincide con el input (mismo item y componentes)
                if (!recipeResult.isEmpty() && 
                    ItemStack.isSameItemSameComponents(recipeResult, inputStack)) {
                    
                    // Receta encontrada! Extraer ingredientes según el tipo
                    List<ItemStack> extractedIngredients = extractIngredients(recipe);
                    if (!extractedIngredients.isEmpty()) {
                        return extractedIngredients;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ingredients;
    }

    /**
     * Extrae ingredientes de cualquier tipo de receta
     * Maneja: ShapedRecipe, ShapelessRecipe, y cualquier otra que tenga getIngredients()
     */
    private static List<ItemStack> extractIngredients(Recipe<?> recipe) {
        List<ItemStack> ingredients = new ArrayList<>();

        try {
            // Intentar obtener ingredientes de la receta
            // La mayoría de recetas implementan este método
            if (recipe instanceof ShapedRecipe shapedRecipe) {
                for (var ingredient : shapedRecipe.getIngredients()) {
                    ItemStack extracted = extractFromIngredient(ingredient);
                    if (!extracted.isEmpty()) {
                        ingredients.add(extracted);
                    }
                }
                return ingredients;
            } 
            else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                for (var ingredient : shapelessRecipe.getIngredients()) {
                    ItemStack extracted = extractFromIngredient(ingredient);
                    if (!extracted.isEmpty()) {
                        ingredients.add(extracted);
                    }
                }
                return ingredients;
            }
            // Para otros tipos de recetas, intentar acceder directamente a getIngredients()
            else {
                try {
                    var ingredientsMethod = recipe.getClass().getMethod("getIngredients");
                    @SuppressWarnings("unchecked")
                    var ingredientsList = (java.util.List<?>) ingredientsMethod.invoke(recipe);
                    
                    for (Object ingredientObj : ingredientsList) {
                        if (ingredientObj instanceof net.minecraft.world.item.crafting.Ingredient ingredient) {
                            ItemStack extracted = extractFromIngredient(ingredient);
                            if (!extracted.isEmpty()) {
                                ingredients.add(extracted);
                            }
                        }
                    }
                    return ingredients;
                } catch (Exception e) {
                    // Si falla, intentar acceso directo a ingredientes
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ingredients;
    }

    /**
     * Extrae un ItemStack de un Ingredient
     * Un Ingredient puede tener múltiples items, extraemos el primero
     */
    private static ItemStack extractFromIngredient(net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }

        try {
            var items = ingredient.getItems();
            if (items != null && items.length > 0) {
                ItemStack copy = items[0].copy();
                copy.setCount(1);
                return copy;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ItemStack.EMPTY;
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
                    // Crear nueva instancia de ItemEnchantments con solo este encantamiento
                    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    mutable.set(enchantmentEntry.getKey(), enchantmentEntry.getValue());
                    enchantedBook.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
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
