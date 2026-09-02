package com.valhyi.recyclertable.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class RecyclerLogic {

    private static final int MIN_DURABILITY_PERCENTAGE = 85;

    public static boolean canRecycle(ItemStack itemStack, Level level) {
        if (itemStack.isEmpty() || level.isClientSide()) {
            return false;
        }

        // Verificar si el item tiene durabilidad y si está por encima del 85%
        if (itemStack.getDamageValue() > 0) {
            int maxDamage = itemStack.getMaxDamage();
            int currentDamage = itemStack.getDamageValue();
            int durabilityPercentage = ((maxDamage - currentDamage) * 100) / maxDamage;
            return durabilityPercentage >= MIN_DURABILITY_PERCENTAGE;
        }

        // Si no tiene durabilidad pero tiene receta, es reciclable
        return hasRecipe(itemStack, level);
    }

    public static boolean hasRecipe(ItemStack itemStack, Level level) {
        if (level.isClientSide() || level.getServer() == null) {
            return false;
        }
        
        // TODO: Implementar búsqueda de receta en el servidor
        // Por ahora retornamos true si el item no tiene durabilidad
        return itemStack.getMaxDamage() == 0;
    }

    public static List<ItemStack> getRecipeIngredients(ItemStack inputStack, Level level) {
        List<ItemStack> ingredients = new ArrayList<>();

        if (inputStack.isEmpty() || level.isClientSide() || level.getServer() == null) {
            return ingredients;
        }

        // TODO: Implementar extracción de ingredientes de receta
        // Esta será la parte principal de la lógica
        
        return ingredients;
    }

    public static List<ItemStack> processRecycling(ItemStack inputStack, ItemStack emptyBottle, ItemStack book, Level level) {
        List<ItemStack> results = new ArrayList<>();

        if (!canRecycle(inputStack, level)) {
            results.add(inputStack.copy());
            return results;
        }

        List<ItemStack> ingredients = getRecipeIngredients(inputStack, level);
        
        boolean isEnchanted = inputStack.hasEnchantments();
        boolean hasEmptyBottle = !emptyBottle.isEmpty();
        boolean hasBook = !book.isEmpty();

        // Si está encantado
        if (isEnchanted) {
            // Validar que tenga los recursos necesarios
            if (!hasEmptyBottle || !hasBook) {
                // No puede procesar, devolver item original
                results.add(inputStack.copy());
                return results;
            }

            // Devolver ingredientes
            results.addAll(ingredients);

            // TODO: Botella de XP
            // TODO: Libro con encantamientos (copiar encantamientos del item)
        } else {
            // Sin encantamientos, solo devolver ingredientes
            results.addAll(ingredients);
        }

        return results;
    }
}
