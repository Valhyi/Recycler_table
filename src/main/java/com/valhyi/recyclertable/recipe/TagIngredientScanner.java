package com.valhyi.recyclertable.recipe;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES: Detecta grupos de items intercambiables usados como ingrediente en
 * recetas de crafting (ej. cualquier tabla de madera para la mesa de
 * crafteo). En vez de depender del sistema de tags de Minecraft (nombre del
 * tag, enumerar todos los tags registrados - API que resultó muy inestable
 * en esta versión), identifica cada grupo directamente por el CONJUNTO
 * EXACTO de items que ese ingrediente acepta. Dos ingredientes con
 * exactamente los mismos items son "el mismo grupo", sin importar si están
 * respaldados por un tag real o no.
 *
 * La clave de cada grupo es un String canónico (items ordenados por su id de
 * registro y unidos con "|").
 *
 * ES: `expandGroupedVariants` (usado por MultiRecipeScanner y RecyclerLogic)
 * es la parte de esta clase que SÍ está conectada: dada la muestra base de
 * una receta, genera una muestra alternativa por cada item de un grupo
 * multi-item (ej. cada tipo de plank), sustituyendo TODAS las posiciones que
 * comparten ese grupo a la vez (nunca una posición sola), para que "mesa de
 * crafteo" o "cama" aparezcan como conflicto normal con una variante por
 * tipo de madera en vez de perderse detrás de siempre muestrear oak. El
 * resto de la clase (scan/getUsedGroups) queda para un sistema de grupos
 * más ambicioso que no se conectó todavía.
 */
public class TagIngredientScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ES: clave canónica -> lista de items del grupo (para mostrar en el panel)
    private static Map<String, List<Item>> usedGroups = Collections.emptyMap();
    private static volatile boolean scanned = false;

    public static void scan(RecipeManager recipeManager) {
        Map<String, List<Item>> found = new HashMap<>();

        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.CRAFTING)) {
            Recipe<?> recipe = holder.value();

            List<Ingredient> ingredients;
            try {
                ingredients = recipe.placementInfo().ingredients();
            } catch (Exception ex) {
                continue;
            }

            for (Ingredient ingredient : ingredients) {
                List<Item> items = ingredient.items().map(Holder::value).distinct().toList();
                if (items.size() < 2) continue;

                String key = canonicalKey(items);
                found.putIfAbsent(key, sortedCopy(items));
            }
        }

        usedGroups = found;
        scanned = true;

        LOGGER.info("[RecyclerTable] Escaneo de grupos de ingrediente completo: "
                + found.size() + " grupo(s) usados en recetas");
        for (Map.Entry<String, List<Item>> entry : found.entrySet()) {
            LOGGER.info("[RecyclerTable]   " + entry.getKey());
        }
    }

    public static boolean isScanned() {
        return scanned;
    }

    public static Map<String, List<Item>> getUsedGroups() {
        return usedGroups;
    }

    public static List<Item> getMembersFor(String groupKey) {
        return usedGroups.getOrDefault(groupKey, Collections.emptyList());
    }

    /**
     * ES: Clave canónica y estable para un conjunto de items: ordenados por
     * su id de registro (namespace:path) y unidos con "|". Mismo conjunto de
     * items siempre produce la misma clave, sin importar el orden original.
     */
    public static String canonicalKey(Collection<Item> items) {
        List<Item> sorted = sortedCopy(items);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(BuiltInRegistries.ITEM.getKey(sorted.get(i)));
        }
        return sb.toString();
    }

    private static List<Item> sortedCopy(Collection<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        return sorted;
    }

    /**
     * ES: Dada la muestra base (1 ItemStack por posición de la receta,
     * mismo orden que recipeIngredients) agrupa las posiciones que
     * comparten EXACTAMENTE el mismo conjunto de items (ej. "planks"
     * repetida en las 4 esquinas de la mesa de crafteo) y devuelve, por
     * cada grupo con 2+ items posibles, una lista de muestras alternativas:
     * una por cada item del grupo, con TODAS las posiciones de ese grupo
     * sustituidas a la vez (para no generar mezclas sin sentido tipo
     * "3 oak + 1 birch"). Grupos de un solo item (ingrediente fijo, no tag)
     * no generan nada. excludeItem se salta como candidato (evita loops de
     * auto-referencia, igual que sampleFromExcluding en RecyclerLogic). Los
     * tintes (DyeItem) tampoco se ofrecen como candidato: no son un
     * "material base" recuperable.
     */
    public static List<List<ItemStack>> expandGroupedVariants(List<Ingredient> recipeIngredients, List<ItemStack> baseSamples, Item excludeItem) {
        List<List<ItemStack>> result = new ArrayList<>();

        Map<String, List<Integer>> positionsByKey = new HashMap<>();
        Map<String, List<Item>> itemsByKey = new HashMap<>();
        for (int i = 0; i < recipeIngredients.size(); i++) {
            List<Item> items = recipeIngredients.get(i).items().map(Holder::value).distinct().toList();
            if (items.size() < 2) continue;

            String key = canonicalKey(items);
            positionsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            itemsByKey.putIfAbsent(key, items);
        }

        for (Map.Entry<String, List<Integer>> group : positionsByKey.entrySet()) {
            List<Integer> positions = group.getValue();
            List<Item> groupItems = itemsByKey.get(group.getKey());

            for (Item candidate : groupItems) {
                if (candidate == excludeItem) continue;
                if (candidate instanceof DyeItem) continue;

                List<ItemStack> variant = new ArrayList<>(baseSamples.size());
                for (ItemStack sample : baseSamples) {
                    variant.add(sample.copy());
                }
                for (int pos : positions) {
                    variant.set(pos, new ItemStack(candidate));
                }
                result.add(variant);
            }
        }

        return result;
    }
}
