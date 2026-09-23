package com.valhyi.recyclertable.recipe;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
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
 * registro y unidos con "|"), para que sea trivial de guardar en el Codec de
 * RecyclerGroupPreferences sin depender de ningún tipo de dato adicional.
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
}
