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
 *
 * ES: Además del mapa plano de siempre (multiRecipeItems), este escaneo
 * también arma unlinkedGroupsByTarget: para items cuya receta tiene 2+
 * grupos de ingrediente intercambiable SIN material en común (ej. fogata:
 * logs + coals; cama: planks + wool — ver TagIngredientScanner.groupsShareMaterial),
 * guarda cada grupo POR SEPARADO en vez de mezclar sus variantes en una
 * sola lista plana. Esto es lo que permite que el panel de tags muestre una
 * fila de conflicto independiente por grupo (con su propio ícono y su
 * propia cuenta de variantes) en vez del bug anterior: un solo ícono
 * repetido y una cuenta de variantes inflada, producto de mezclar
 * variantes de grupos que en realidad son independientes entre sí (elegir
 * el tipo de log no tiene nada que ver con elegir carbón vs carbón
 * vegetal). El caso enlazado (barril: planks+slabs comparten material) NO
 * entra aquí — sigue resuelto como una sola fila por multiRecipeItems, tal
 * como ya funcionaba.
 *
 * Este mapa es puramente aditivo: no reemplaza ni modifica
 * multiRecipeItems, RecyclerPreferences, ni RecyclerLogic. Por ahora solo
 * expone los datos (hasUnlinkedGroups / getUnlinkedGroupsFor); conectarlo al
 * panel de tags (RecyclerScreen) y a la resolución de preferencias
 * (RecyclerPreferences / RecyclerLogic) es el siguiente paso pendiente.
 */
public class MultiRecipeScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * ES: Una "variante" = la firma de items (uno por ingrediente, en orden)
     * de UNA receta que produce el item objetivo. Dos recetas distintas que
     * casualmente pidan exactamente los mismos items no se cuentan dos veces.
     */
    public record RecipeVariant(List<Item> ingredientItems) {}

    /**
     * ES: Un grupo de variantes que comparten origen (mismo grupo de
     * ingrediente intercambiable dentro de una receta, ej. "coals" o
     * "logs" en la fogata). "key" es la clave canónica del grupo (ver
     * TagIngredientScanner.canonicalKey) - estable entre escaneos mientras
     * el conjunto de items del grupo no cambie. "positions" son los índices
     * dentro de la firma completa (RecipeVariant.ingredientItems) que este
     * grupo realmente controla - necesario para saber qué ítem de la firma
     * usar como ícono (ver getGroupDisplayItem), ya que NO siempre es la
     * posición 0 (ej. en la fogata, el grupo "coals" está en la posición 4,
     * no en la 0).
     */
    public record VariantGroup(String key, List<Integer> positions, List<RecipeVariant> variants) {}

    private static Map<Item, List<RecipeVariant>> multiRecipeItems = Collections.emptyMap();

    // ES: Por cada item objetivo, la posición del ingrediente que REALMENTE
    // cambia entre sus variantes (ver computeDisplayIndex). El panel de tags
    // usa esto para dibujar el ícono correcto en el grid: sin esto, siempre
    // se mostraba ingredientItems().get(0), que en recetas como la cama
    // (lana fija en la posición 0, tabla variable en otra posición) mostraba
    // 12 veces el mismo ícono de lana en vez de las distintas tablas.
    private static Map<Item, Integer> displayIndexByTarget = Collections.emptyMap();

    // ES: Ver el bloque de comentarios de la clase. Solo tiene entradas para
    // items cuya receta tiene 2+ grupos SIN material en común entre sí.
    private static Map<Item, List<VariantGroup>> unlinkedGroupsByTarget = Collections.emptyMap();

    private static volatile boolean scanned = false;

    public static void scan(RecipeManager recipeManager) {
        Map<Item, List<RecipeVariant>> found = new HashMap<>();
        Map<Item, List<VariantGroup>> unlinkedGroups = new HashMap<>();

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

            // ES: NUEVO — si esta receta tiene 2+ grupos de ingrediente
            // intercambiable SIN material en común, registrarlos también
            // como grupos independientes (ver comentario de clase).
            List<TagIngredientScanner.IngredientGroup> detectedGroups = TagIngredientScanner.detectGroups(recipeIngredients);
            if (detectedGroups.size() >= 2 && !TagIngredientScanner.groupsShareMaterial(detectedGroups)) {
                registerUnlinkedGroups(unlinkedGroups, target, baseSamples, detectedGroups);
            }
        }

        // ES: Solo interesan los items con 2+ recetas distintas (conflicto real),
        // y que no estén ya en la blacklist (esos nunca se reciclan, no tiene
        // sentido mostrarlos en el panel de configuración).
        found.entrySet().removeIf(entry -> entry.getValue().size() < 2
                || new ItemStack(entry.getKey()).is(RecyclerLogic.BLACKLISTED_FROM_RECYCLING));

        // ES: Un target solo puede tener grupos independientes si sigue
        // siendo un conflicto real tras el filtro de arriba (ej. si quedó
        // fuera por blacklist, tampoco tiene sentido mostrar sus grupos).
        unlinkedGroups.keySet().retainAll(found.keySet());

        Map<Item, Integer> displayIndex = new HashMap<>();
        for (Map.Entry<Item, List<RecipeVariant>> entry : found.entrySet()) {
            displayIndex.put(entry.getKey(), computeDisplayIndex(entry.getValue()));
        }

        multiRecipeItems = found;
        displayIndexByTarget = displayIndex;
        unlinkedGroupsByTarget = unlinkedGroups;
        scanned = true;

        LOGGER.info("[RecyclerTable] Escaneo de recetas multiples completo: "
                + found.size() + " item(s) con conflicto");
        for (Map.Entry<Item, List<RecipeVariant>> entry : found.entrySet()) {
            LOGGER.info("[RecyclerTable]   " + entry.getKey() + " -> " + entry.getValue());
        }

        LOGGER.info("[RecyclerTable] Escaneo de grupos independientes completo: "
                + unlinkedGroups.size() + " item(s) con grupos sin material en comun");
        for (Map.Entry<Item, List<VariantGroup>> entry : unlinkedGroups.entrySet()) {
            for (VariantGroup group : entry.getValue()) {
                LOGGER.info("[RecyclerTable]   " + entry.getKey() + " / grupo \"" + group.key()
                        + "\" -> " + group.variants().size() + " variante(s)");
            }
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
     * ES: Para cada grupo detectado en la receta, arma sus propias
     * RecipeVariant (una por item del grupo, sustituyendo SOLO las
     * posiciones de ese grupo sobre la base — todo lo demás queda en su
     * valor base) y las guarda bajo su propia clave, sin mezclarlas con las
     * de otros grupos. Si dos recetas distintas que producen el mismo
     * target comparten la misma clave de grupo, no se duplica el grupo.
     */
    private static void registerUnlinkedGroups(Map<Item, List<VariantGroup>> unlinkedGroups, Item target,
                                                 List<ItemStack> baseSamples,
                                                 List<TagIngredientScanner.IngredientGroup> groups) {
        List<VariantGroup> existingGroups = unlinkedGroups.computeIfAbsent(target, k -> new ArrayList<>());

        for (TagIngredientScanner.IngredientGroup group : groups) {
            boolean alreadyRegistered = existingGroups.stream().anyMatch(vg -> vg.key().equals(group.key()));
            if (alreadyRegistered) continue;

            List<RecipeVariant> variants = new ArrayList<>();
            for (Item candidate : group.items()) {
                if (candidate == target) continue;
                if (candidate instanceof DyeItem) continue;

                List<ItemStack> variantSamples = new ArrayList<>(baseSamples.size());
                for (ItemStack sample : baseSamples) {
                    variantSamples.add(sample.copy());
                }
                for (int pos : group.positions()) {
                    variantSamples.set(pos, new ItemStack(candidate));
                }

                List<Item> signature = variantSamples.stream().map(ItemStack::getItem).toList();
                variants.add(new RecipeVariant(signature));
            }

            if (!variants.isEmpty()) {
                existingGroups.add(new VariantGroup(group.key(), group.positions(), variants));
            }
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

    /**
     * ES: true si este target tiene 2+ grupos de ingrediente intercambiable
     * SIN material en común entre sí (ej. fogata, cama). El panel de tags
     * puede usar esto para decidir si debe mostrar varias filas de
     * conflicto para este item en vez de una sola.
     */
    public static boolean hasUnlinkedGroups(Item target) {
        return unlinkedGroupsByTarget.containsKey(target);
    }

    /**
     * ES: Los grupos independientes de este target (vacío si
     * hasUnlinkedGroups(target) es false). Cada VariantGroup trae su propia
     * clave y su propia lista de variantes, ya lista para usarse como una
     * fila de conflicto separada.
     */
    public static List<VariantGroup> getUnlinkedGroupsFor(Item target) {
        return unlinkedGroupsByTarget.getOrDefault(target, Collections.emptyList());
    }

    /**
     * ES: Ítem representativo de un grupo independiente, para usar como
     * ícono de esa fila en el panel de tags: se lee de la primera posición
     * real del grupo (group.positions()) dentro de la primera variante, NO
     * de la posición 0 de la firma completa (que puede pertenecer a otro
     * grupo, o ser un ingrediente fijo — ej. el stick de la fogata).
     */
    public static Item getGroupDisplayItem(VariantGroup group) {
        if (group.variants().isEmpty() || group.positions().isEmpty()) return null;
        List<Item> firstSignature = group.variants().get(0).ingredientItems();
        int pos = group.positions().get(0);
        return pos >= 0 && pos < firstSignature.size() ? firstSignature.get(pos) : null;
    }
}
