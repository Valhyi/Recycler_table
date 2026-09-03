package com.valhyi.recyclertable.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            var recipe = recipeHolder.value();
            
            // Verificar que sea una receta de crafteo (Shaped o Shapeless)
            if (recipe instanceof ShapedRecipe shapedRecipe) {
                // Comparar directamente con el item
                try {
                    ItemStack result = shapedRecipe.result.create();
                    if (result.is(itemStack.getItem())) {
                        return true;
                    }
                } catch (Exception e) {
                    // Si no puede acceder a result, intentar otra forma
                }
            } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                // Comparar directamente con el item
                try {
                    ItemStack result = shapelessRecipe.result.create();
                    if (result.is(itemStack.getItem())) {
                        return true;
                    }
                } catch (Exception e) {
                    // Si no puede acceder a result, intentar otra forma
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
        for (RecipeHolder<?> recipeHolder : recipeManager.getRecipes()) {
            var recipe = recipeHolder.value();
            
            // Verificar que sea una receta válida (Shaped)
            if (recipe instanceof ShapedRecipe shapedRecipe) {
                try {
                    ItemStack result = shapedRecipe.result.create();
                    if (result.is(inputStack.getItem())) {
                        // Obtener ingredientes de la receta (List<Optional<Ingredient>>)
                        for (Optional<Ingredient> optionalIngredient : shapedRecipe.getIngredients()) {
                            if (optionalIngredient.isPresent()) {
                                Ingredient ingredient = optionalIngredient.get();
                                // Usar ingredient.items() para obtener los items
                                var firstItem = ingredient.items().findFirst();
                                if (firstItem.isPresent()) {
                                    ItemStack copy = firstItem.get().value().getDefaultInstance().copy();
                                    copy.setCount(1);
                                    ingredients.add(copy);
                                }
                            }
                        }
                        return ingredients;
                    }
                } catch (Exception e) {
                    // Ignorar si no puede acceder
                }
            } 
            // Verificar que sea una receta válida (Shapeless)
            else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                try {
                    ItemStack result = shapelessRecipe.result.create();
                    if (result.is(inputStack.getItem())) {
                        // Obtener ingredientes usando acceso directo a ingredients field
                        for (Ingredient ingredient : shapelessRecipe.ingredients) {
                            var firstItem = ingredient.items().findFirst();
                            if (firstItem.isPresent()) {
                                ItemStack copy = firstItem.get().value().getDefaultInstance().copy();
                                copy.setCount(1);
                                ingredients.add(copy);
                            }
                        }
                        return ingredients;
                    }
                } catch (Exception e) {
                    // Ignorar si no puede acceder
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
