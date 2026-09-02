package com.valhyi.recyclertable.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        return itemStack.getMaxDamage() == 0;
    }

    public static List<ItemStack> getRecipeIngredients(ItemStack inputStack, Level level) {
        List<ItemStack> ingredients = new ArrayList<>();

        if (inputStack.isEmpty() || level.isClientSide() || level.getServer() == null) {
            return ingredients;
        }

        // TODO: Implementar extracción de ingredientes de receta
        
        return ingredients;
    }

    public static List<ItemStack> processRecycling(ItemStack inputStack, ItemStack emptyBottle, ItemStack book, Level level) {
        List<ItemStack> results = new ArrayList<>();

        if (!canRecycle(inputStack, level)) {
            // Si no puede reciclarse, enviar al output sin cambios
            results.add(inputStack.copy());
            return results;
        }

        List<ItemStack> ingredients = getRecipeIngredients(inputStack, level);
        
        // Verificar si tiene encantamientos - Minecraft 1.20.6+ usa getAllEnchantments con RegistryAccess
        boolean isEnchanted = !inputStack.getAllEnchantments().isEmpty();
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
            copyAllEnchantments(inputStack, enchantedBook);
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
     * Copia TODOS los encantamientos del item origen al libro (Cut & Paste completo)
     * Mantiene los niveles de encantamiento exactamente igual: Sharpness V -> Sharpness V
     */
    private static void copyAllEnchantments(ItemStack source, ItemStack target) {
        // Obtener todos los encantamientos del item fuente
        var enchantments = source.getAllEnchantments();
        
        // Copiar cada encantamiento al libro
        enchantments.forEach((enchantmentHolder, level) -> {
            target.enchant(enchantmentHolder, level);
        });
    }
}
