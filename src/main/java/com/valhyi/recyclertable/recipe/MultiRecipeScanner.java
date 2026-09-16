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
    private static volatile boolean scanned = false;

    public static void scan(RecipeManager recipeManager) {
        Map<Item, List<RecipeVariant>> found = new HashMap<>();

        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.CRAFTING)) {
            Recipe<?> recipe = holder.value();

            List<Ingredient> recipeIngredients = recipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> samples = new ArrayList<>();
            boolean anyEmpty = false;
            for (Ingredient ingredient : recipeIngredients) {
                var first = ingredient.items().findFirst();
                if (first.isEmpty()) {
                    anyEmpty = true;
                    break;
                }
                samples.add(new ItemStack(first.get().value()));
            }
            if (anyEmpty) continue;

            // ES: Recetas de reteñido (tinte + item de cualquier color -> item de
            // otro color) no son "materiales base", se descartan igual que en
            // RecyclerLogic.findInCrafting.
            boolean isDyeRecipe = samples.stream().anyMatch(s -> s.getItem() instanceof DyeItem);
            if (isDyeRecipe) continue;

            CraftingInput input = CraftingInput.of(samples.size(), 1, samples);
            ItemStack output;
            try {
                @SuppressWarnings("unchecked")
                Recipe<CraftingInput> craftingRecipe = (Recipe<CraftingInput>) recipe;
                output = craftingRecipe.assemble(input);
            } catch (Exception ex) {
                // ES: Algunas recetas especiales no aceptan un input sintético
                // armado así; se ignoran y se sigue con la siguiente.
                continue;
            }
            if (output.isEmpty()) continue;

            Item target = output.getItem();
            List<Item> signature = samples.stream().map(ItemStack::getItem).toList();

            List<RecipeVariant> variants = found.computeIfAbsent(target, k -> new ArrayList<>());
            boolean alreadyHasSignature = variants.stream()
                    .anyMatch(v -> v.ingredientItems().equals(signature));
            if (!alreadyHasSignature) {
                variants.add(new RecipeVariant(signature));
            }
        }

        // ES: Solo interesan los items con 2+ recetas distintas (conflicto real),
        // y que no estén ya en la blacklist (esos nunca se reciclan, no tiene
        // sentido mostrarlos en el panel de configuración).
        found.entrySet().removeIf(entry -> entry.getValue().size() < 2
                || new ItemStack(entry.getKey()).is(RecyclerLogic.BLACKLISTED_FROM_RECYCLING));

        multiRecipeItems = found;
        scanned = true;

        LOGGER.info("[RecyclerTable] Escaneo de recetas multiples completo: "
                + found.size() + " item(s) con conflicto");
        for (Map.Entry<Item, List<RecipeVariant>> entry : found.entrySet()) {
            LOGGER.info("[RecyclerTable]   " + entry.getKey() + " -> " + entry.getValue());
        }
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
}
