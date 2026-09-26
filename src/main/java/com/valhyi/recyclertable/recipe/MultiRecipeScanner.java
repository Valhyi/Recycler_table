package com.valhyi.recyclertable.recipe;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES: Escanea TODAS las recetas de tipo crafting UNA SOLA VEZ (al iniciar el
 * server, igual que hace JEI en su fase de registro) y arma un mapa de
 * "items con más de una receta de crafting válida que los produce"
 * (ej. mossy_cobblestone: cobblestone+vine O cobblestone+moss_block).
 *
 * Además de recetas realmente distintas (RecipeHolder distintos), también
 * cuenta como "variante" cada item alternativo de un ingrediente por tag
 * dentro de UNA misma receta (ej. crafting_table o las camas, que aceptan
 * cualquier tipo de plank): ver TagIngredientScanner.expandGroupedVariants.
 * Sin esto, esas recetas nunca aparecían como conflicto porque siempre se
 * muestreaba el primer item del tag (típicamente oak).
 *
 * El resultado queda cacheado en memoria. Ni RecyclerLogic ni el panel de
 * tags vuelven a tocar el RecipeManager para esta lista; solo leen
 * getVariantsFor(item).
 *
 * NOTA: si se usa /reload en el server, este caché queda desactualizado
 * hasta el próximo reinicio. Aceptable para v1; se puede enganchar también
 * a un evento de recarga de datapacks más adelante si hace falta.
 */
public class MultiRecipeScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * ES: Una "variante" = la firma de items (uno por ingrediente, en orden)
     * de UNA receta que produce el item objetivo. Dos recetas distintas que
     * casualmente pidan exactamente los mismos items no se cuentan dos veces.
     */
    public record RecipeVariant(List<Item> ingredientItems) {}

    private static Map<Item, List<RecipeVariant>> multiRecipeItems = Collections.emptyMap();

    // ES: Por cada item objetivo, la posición del ingrediente que REALMENTE
    // cambia entre sus variantes (ver computeDisplayIndex). El panel de tags
    // usa esto para dibujar el ícono correcto en el grid: sin esto, siempre
    // se mostraba ingredientItems().get(0), que en recetas como la cama
    // (lana fija en la posición 0, tabla variable en otra posición) mostraba
    // 12 veces el mismo ícono de lana en vez de las distintas tablas.
    private static Map<Item, Integer> displayIndexByTarget = Collections.emptyMap();

    private static volatile boolean scanned = false;

    public static void scan(RecipeManager recipeManager) {
        Map<Item, List<RecipeVariant>> found = new HashMap<>();

        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.CRAFTING)) {
            Recipe<?> recipe = holder.value();

            List<Ingredient> recipeIngredients = recipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> baseSamples = new ArrayList<>();
            boolean anyEmpty = false;
            for (Ingredient ingredient : recipeIngredients) {
                var first = ingredient.items().findFirst();
                if (first.isEmpty()) {
                    anyEmpty = true;
                    break;
                }
                baseSamples.add(new ItemStack(first.get().value()));
            }
            if (anyEmpty) continue;

            // ES: Recetas de reteñido (tinte + item de cualquier color -> item de
            // otro color) no son "materiales base", se descartan igual que en
            // RecyclerLogic.findInCrafting.
            boolean isDyeRecipe = baseSamples.stream().anyMatch(s -> s.getItem() instanceof DyeItem);
            if (isDyeRecipe) continue;

            @SuppressWarnings("unchecked")
            Recipe<CraftingInput> craftingRecipe = (Recipe<CraftingInput>) recipe;

            ItemStack baseOutput;
            try {
                baseOutput = craftingRecipe.assemble(CraftingInput.of(baseSamples.size(), 1, baseSamples));
            } catch (Exception ex) {
                // ES: Algunas recetas especiales no aceptan un input sintético
                // armado así; se ignoran y se sigue con la siguiente.
                continue;
            }
            if (baseOutput.isEmpty()) continue;

            Item target = baseOutput.getItem();
            registerVariant(found, target, baseSamples);

            // ES: Ingredientes por tag (varios items posibles) dentro de esta
            // misma receta: una variante adicional por cada item del tag.
            for (List<ItemStack> variantSamples : TagIngredientScanner.expandGroupedVariants(recipeIngredients, baseSamples, target)) {
                ItemStack variantOutput;
                try {
                    variantOutput = craftingRecipe.assemble(CraftingInput.of(variantSamples.size(), 1, variantSamples));
                } catch (Exception ex) {
                    continue;
                }
                if (variantOutput.isEmpty() || variantOutput.getItem() != target) continue;

                registerVariant(found, target, variantSamples);
            }
        }

        // ES: Solo interesan los items con 2+ recetas distintas (conflicto real),
        // y que no estén ya en la blacklist (esos nunca se reciclan, no tiene
        // sentido mostrarlos en el panel de configuración).
        found.entrySet().removeIf(entry -> entry.getValue().size() < 2
                || new ItemStack(entry.getKey()).is(RecyclerLogic.BLACKLISTED_FROM_RECYCLING));

        Map<Item, Integer> displayIndex = new HashMap<>();
        for (Map.Entry<Item, List<RecipeVariant>> entry : found.entrySet()) {
            displayIndex.put(entry.getKey(), computeDisplayIndex(entry.getValue()));
        }

        multiRecipeItems = found;
        displayIndexByTarget = displayIndex;
        scanned = true;

        LOGGER.info("[RecyclerTable] Escaneo de recetas multiples completo: "
                + found.size() + " item(s) con conflicto");
        for (Map.Entry<Item, List<RecipeVariant>> entry : found.entrySet()) {
            LOGGER.info("[RecyclerTable]   " + entry.getKey() + " -> " + entry.getValue());
        }
    }

    private static void registerVariant(Map<Item, List<RecipeVariant>> found, Item target, List<ItemStack> samples) {
        List<Item> signature = samples.stream().map(ItemStack::getItem).toList();
        List<RecipeVariant> variants = found.computeIfAbsent(target, k -> new ArrayList<>());
        boolean alreadyHasSignature = variants.stream()
                .anyMatch(v -> v.ingredientItems().equals(signature));
        if (!alreadyHasSignature) {
            variants.add(new RecipeVariant(signature));
        }
    }

    /**
     * ES: Encuentra la primera posición de ingrediente que NO es igual en
     * todas las variantes de la lista (la posición "que realmente cambia").
     * Si por algún motivo ninguna posición varía (no debería pasar, ya que
     * registerVariant descarta firmas duplicadas), devuelve 0 como respaldo.
     */
    private static int computeDisplayIndex(List<RecipeVariant> variants) {
        if (variants.isEmpty()) return 0;

        int maxLen = 0;
        for (RecipeVariant v : variants) {
            maxLen = Math.max(maxLen, v.ingredientItems().size());
        }

        for (int i = 0; i < maxLen; i++) {
            Item reference = null;
            boolean referenceSet = false;
            boolean varies = false;

            for (RecipeVariant v : variants) {
                List<Item> items = v.ingredientItems();
                Item current = i < items.size() ? items.get(i) : null;

                if (!referenceSet) {
                    reference = current;
                    referenceSet = true;
                } else if (current != reference) {
                    varies = true;
                    break;
                }
            }

            if (varies) return i;
        }

        return 0;
    }

    public static boolean isScanned() {
        return scanned;
    }

    public static Map<Item, List<RecipeVariant>> getMultiRecipeItems() {
        return multiRecipeItems;
    }

    public static List<RecipeVariant> getVariantsFor(Item item) {
        return multiRecipeItems.getOrDefault(item, Collections.emptyList());
    }

    /**
     * ES: Devuelve el Item que se debe usar como ícono para esta variante de
     * este item objetivo: la posición que realmente distingue una variante
     * de otra (ver computeDisplayIndex), no siempre la posición 0.
     */
    public static Item getDisplayItem(Item target, RecipeVariant variant) {
        List<Item> items = variant.ingredientItems();
        if (items.isEmpty()) return target;

        int idx = displayIndexByTarget.getOrDefault(target, 0);
        if (idx < 0 || idx >= items.size()) idx = 0;
        return items.get(idx);
    }
}
