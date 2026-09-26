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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * una receta, genera muestras alternativas por cada grupo de ingrediente con
 * 2+ items posibles (ej. cada tipo de plank). Cuando una receta tiene VARIOS
 * grupos que representan el mismo "material" (ej. barril: 6 posiciones de
 * tablas + 2 posiciones de losas, ambos con los mismos tipos de madera), los
 * grupos se ENLAZAN: se genera una sola variante por material, sustituyendo
 * TODOS los grupos enlazados a la vez (elegir cerezo pone tablas de cerezo Y
 * losa de cerezo), en vez de una explosión combinatoria de variantes
 * "solo tablas cambian" + "solo losas cambian". Si los grupos de una receta
 * no comparten ningún material en común, se usa el comportamiento anterior
 * (cada grupo expandido por separado) como respaldo.
 *
 * ES: `detectGroups` / `groupsShareMaterial` son la versión "cruda" de ese
 * mismo análisis, usada por MultiRecipeScanner para decidir si una receta
 * con 2+ grupos debe exponerse como VARIAS filas de conflicto independientes
 * en el panel de tags (grupos sin material en común, ej. fogata: logs +
 * coals) o como una sola (grupos enlazados, ej. barril). No generan
 * muestras de ItemStack, solo devuelven qué posiciones/items componen cada
 * grupo; expandGroupedVariants sigue siendo la única fuente de verdad para
 * armar las muestras reales.
 */
public class TagIngredientScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ES: Sufijos conocidos de "variante de material" en items de madera y
    // similares. Se prueban de más largo a más corto para no cortar mal
    // (ej. "_fence_gate" antes que "_fence"). "stripped_" se saca aparte
    // como prefijo antes de probar sufijos.
    private static final List<String> MATERIAL_SUFFIXES = List.of(
            "_pressure_plate", "_trapdoor", "_fence_gate", "_hanging_sign",
            "_chest_boat", "_stairs", "_slab", "_planks", "_fence", "_door",
            "_button", "_sign", "_boat", "_log", "_wood", "_leaves", "_sapling"
    );

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
     * ES: "Material" de un item para poder enlazar grupos entre sí: el id
     * del item sin el prefijo "stripped_" ni el sufijo de tipo conocido
     * (_planks, _slab, _stairs, etc). "cherry_planks" y "cherry_slab" dan
     * ambos "cherry"; un item sin sufijo reconocido devuelve su id completo
     * (no va a coincidir con nada de otro grupo, lo cual es el
     * comportamiento correcto: sin sufijo conocido, no se asume relación).
     */
    private static String materialKeyFor(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }
        for (String suffix : MATERIAL_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return path.substring(0, path.length() - suffix.length());
            }
        }
        return path;
    }

    private static List<ItemStack> copySamples(List<ItemStack> baseSamples) {
        List<ItemStack> copy = new ArrayList<>(baseSamples.size());
        for (ItemStack sample : baseSamples) {
            copy.add(sample.copy());
        }
        return copy;
    }

    /**
     * ES: Dada la muestra base (1 ItemStack por posición de la receta,
     * mismo orden que recipeIngredients), agrupa las posiciones que
     * comparten EXACTAMENTE el mismo conjunto de items (ej. "planks"
     * repetida en las 4 esquinas de la mesa de crafteo, o "losas de madera"
     * repetida en 2 posiciones del barril).
     *
     * - Si la receta tiene UN solo grupo con 2+ items: una variante por
     *   item del grupo (comportamiento simple, sin cambios).
     * - Si tiene VARIOS grupos y todos comparten al menos un "material" en
     *   común (ver materialKeyFor) para cada material presente en TODOS los
     *   grupos: se generan variantes ENLAZADAS, una por material, que
     *   sustituyen TODOS los grupos a la vez (ej. barril: elegir cerezo
     *   pone tablas de cerezo Y losa de cerezo en una sola variante, en vez
     *   de generar tablas-de-cerezo-con-losa-de-roble y
     *   losa-de-cerezo-con-tablas-de-roble por separado).
     * - Si tiene varios grupos pero NINGÚN material es común a todos: se usa
     *   el comportamiento anterior (cada grupo expandido por separado) como
     *   respaldo, para no romper recetas con tags no relacionados entre sí.
     *
     * excludeItem se salta como candidato (evita loops de auto-referencia,
     * igual que sampleFromExcluding en RecyclerLogic). Los tintes (DyeItem)
     * tampoco se ofrecen como candidato: no son un "material base"
     * recuperable.
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

        if (positionsByKey.isEmpty()) {
            return result;
        }

        if (positionsByKey.size() == 1) {
            // ES: Un solo grupo variable en la receta -> comportamiento simple de siempre.
            Map.Entry<String, List<Integer>> group = positionsByKey.entrySet().iterator().next();
            for (List<ItemStack> variant : expandSingleGroup(baseSamples, group.getValue(), itemsByKey.get(group.getKey()), excludeItem)) {
                result.add(variant);
            }
            return result;
        }

        // ES: Varios grupos - intentar enlazarlos por material compartido.
        // materialKey -> (groupKey -> item de ese grupo con ese material)
        Map<String, Map<String, Item>> materialToGroupItem = new HashMap<>();
        for (Map.Entry<String, List<Item>> entry : itemsByKey.entrySet()) {
            String groupKey = entry.getKey();
            for (Item item : entry.getValue()) {
                if (item instanceof DyeItem) continue;
                String materialKey = materialKeyFor(item);
                materialToGroupItem.computeIfAbsent(materialKey, k -> new HashMap<>()).putIfAbsent(groupKey, item);
            }
        }

        Set<String> allGroupKeys = positionsByKey.keySet();
        List<String> commonMaterials = new ArrayList<>();
        for (Map.Entry<String, Map<String, Item>> entry : materialToGroupItem.entrySet()) {
            if (entry.getValue().keySet().containsAll(allGroupKeys)) {
                commonMaterials.add(entry.getKey());
            }
        }

        if (!commonMaterials.isEmpty()) {
            for (String material : commonMaterials) {
                Map<String, Item> perGroupItem = materialToGroupItem.get(material);

                boolean anyExcluded = perGroupItem.values().stream().anyMatch(item -> item == excludeItem);
                if (anyExcluded) continue;

                List<ItemStack> variant = copySamples(baseSamples);
                for (String groupKey : allGroupKeys) {
                    Item chosen = perGroupItem.get(groupKey);
                    for (int pos : positionsByKey.get(groupKey)) {
                        variant.set(pos, new ItemStack(chosen));
                    }
                }
                result.add(variant);
            }
            return result;
        }

        // ES: Respaldo: ningún material en común entre los grupos, se
        // expande cada grupo por separado (comportamiento anterior).
        for (Map.Entry<String, List<Integer>> group : positionsByKey.entrySet()) {
            result.addAll(expandSingleGroup(baseSamples, group.getValue(), itemsByKey.get(group.getKey()), excludeItem));
        }
        return result;
    }

    private static List<List<ItemStack>> expandSingleGroup(List<ItemStack> baseSamples, List<Integer> positions, List<Item> groupItems, Item excludeItem) {
        List<List<ItemStack>> variants = new ArrayList<>();
        for (Item candidate : groupItems) {
            if (candidate == excludeItem) continue;
            if (candidate instanceof DyeItem) continue;

            List<ItemStack> variant = copySamples(baseSamples);
            for (int pos : positions) {
                variant.set(pos, new ItemStack(candidate));
            }
            variants.add(variant);
        }
        return variants;
    }

    // ================= NUEVO: detección "cruda" de grupos (sin generar ItemStacks) =================

    /**
     * ES: Info de UN grupo de ingrediente intercambiable (2+ items
     * posibles) dentro de una receta: la clave canónica del grupo, las
     * posiciones que ocupa, y los items que acepta.
     */
    public record IngredientGroup(String key, List<Integer> positions, List<Item> items) {}

    /**
     * ES: Detecta los grupos de ingrediente intercambiable de una receta,
     * SIN resolver todavía si están enlazados por material entre sí (eso lo
     * hace groupsShareMaterial). Es la misma detección de posiciones/items
     * que ya hacía expandGroupedVariants internamente, pero expuesta como
     * dato reutilizable en vez de generar muestras directamente.
     *
     * Usado por MultiRecipeScanner para decidir si una receta con 2+ grupos
     * debe exponerse como varias filas de conflicto independientes en el
     * panel de tags (grupos sin material en común, ej. fogata: logs +
     * coals) o como una sola fila (grupos enlazados por material, ej.
     * barril: planks + slabs — ese caso ya lo resuelve
     * expandGroupedVariants generando variantes enlazadas, no requiere
     * filas separadas).
     */
    public static List<IngredientGroup> detectGroups(List<Ingredient> recipeIngredients) {
        Map<String, List<Integer>> positionsByKey = new LinkedHashMap<>();
        Map<String, List<Item>> itemsByKey = new LinkedHashMap<>();

        for (int i = 0; i < recipeIngredients.size(); i++) {
            List<Item> items = recipeIngredients.get(i).items().map(Holder::value).distinct().toList();
            if (items.size() < 2) continue;

            String key = canonicalKey(items);
            positionsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            itemsByKey.putIfAbsent(key, items);
        }

        List<IngredientGroup> result = new ArrayList<>();
        for (String key : positionsByKey.keySet()) {
            result.add(new IngredientGroup(key, positionsByKey.get(key), itemsByKey.get(key)));
        }
        return result;
    }

    /**
     * ES: Determina si TODOS los grupos dados comparten al menos un
     * "material" en común (ver materialKeyFor) que aparezca en cada uno de
     * ellos - el mismo criterio que ya usa expandGroupedVariants para
     * decidir entre "variantes enlazadas" y "cada grupo por separado". Con
     * 0 o 1 grupo se considera "enlazado" por defecto (no hay nada que
     * separar en filas independientes).
     */
    public static boolean groupsShareMaterial(List<IngredientGroup> groups) {
        if (groups.size() < 2) return true;

        Map<String, Set<String>> materialToGroups = new HashMap<>();
        for (IngredientGroup group : groups) {
            for (Item item : group.items()) {
                if (item instanceof DyeItem) continue;
                materialToGroups.computeIfAbsent(materialKeyFor(item), k -> new HashSet<>()).add(group.key());
            }
        }

        Set<String> allGroupKeys = new HashSet<>();
        for (IngredientGroup group : groups) {
            allGroupKeys.add(group.key());
        }

        for (Set<String> groupsForMaterial : materialToGroups.values()) {
            if (groupsForMaterial.containsAll(allGroupKeys)) {
                return true;
            }
        }
        return false;
    }
}
