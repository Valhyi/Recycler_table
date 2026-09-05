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
import com.valhyi.recyclertable.mixin.ShapelessRecipeAccessor;

import java.lang.reflect.Field;
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
        return getRecipeIngredients(itemStack, level).size() > 0;
    }

    /**
     * Obtiene el resultado de una receta (ShapedRecipe o ShapelessRecipe)
     */
    private static ItemStack getRecipeResult(Object recipe) {
        try {
            if (recipe instanceof ShapedRecipe shapedRecipe) {
                // ShapedRecipe: acceder al field 'result' usando reflexión
                Field resultField = ShapedRecipe.class.getDeclaredField("result");
                resultField.setAccessible(true);
                Object resultObj = resultField.get(shapedRecipe);
                
                // resultObj es un ItemStackTemplate, llamar create()
                if (resultObj != null) {
                    return (ItemStack) resultObj.getClass().getMethod("create").invoke(resultObj);
                }
            } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                // ShapelessRecipe: acceder al field 'result'
                Field resultField = ShapelessRecipe.class.getDeclaredField("result");
                resultField.setAccessible(true);
                Object resultObj = resultField.get(shapelessRecipe);
                
                // resultObj es un ItemStackTemplate, llamar create()
                if (resultObj != null) {
                    return (ItemStack) resultObj.getClass().getMethod("create").invoke(resultObj);
                }
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
                    for (Optional<Ingredient> optionalIngredient : shapedRecipe.getIngredients()) {
                        if (optionalIngredient.isPresent()) {
                            Ingredient ingredient = optionalIngredient.get();
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
                // Verificar que sea una receta de Shapeless
                else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                    // Usar el Mixin accessor para acceder a ingredientes privados
                    try {
                        if (shapelessRecipe instanceof ShapelessRecipeAccessor accessor) {
                            for (Ingredient ingredient : accessor.getIngredients()) {
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
                        // Si falla, continuar a siguiente receta
                    }
                }
            }
        }
        
        return ingredients;
    }

    public static List<ItemStack> processRecycling(ItemStack inputStack, ItemStack emptyBottle, ItemStack book, Level level) {
        List<ItemStack> results = new ArrayList<>();

        if (inputStack.isEmpty() || level == null) {
            return results;
        }

        List<ItemStack> ingredients = getRecipeIngredients(inputStack, level);
        
        // Si no hay ingredientes, no se puede reciclar
        if (ingredients.isEmpty()) {
            return results;
        }
        
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
