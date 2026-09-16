package com.valhyi.recyclertable.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class RecyclerLogic {

    // ES: Logger real del juego (en vez de System.out.println), para que los
    // mensajes de debug aparezcan en latest.log sin importar el launcher usado
    // (Lunar Client no siempre captura System.out).
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * ES: Tag de datapack para excluir items del reciclaje por completo
     * (metales, gemas, nuggets, comida, piedra/cobblestone, etc).
     * Archivo: data/recyclertable/tags/item/blacklisted_from_recycling.json
     */
    public static final TagKey<Item> BLACKLISTED_FROM_RECYCLING =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("recyclertable", "blacklisted_from_recycling"));

    /**
     * ES: Resultado de encontrar la receta que produce el item objetivo.
     * ingredients: 1 copia de cada ingrediente necesario para UNA aplicación de la receta.
     * outputCount: cuántas unidades del item objetivo produce UNA aplicación de la receta.
     */
    public record RecipeMatch(List<ItemStack> ingredients, int outputCount) {}

    /**
     * ES: Resultado final de procesar un stack completo del slot de proceso.
     */
    public record RecyclingOutput(List<ItemStack> results, int bottlesConsumed, int booksConsumed) {}

    public static boolean canRecycle(ItemStack itemStack, Level level) {
        return !itemStack.isEmpty() && !level.isClientSide();
    }

    public static boolean isBlacklisted(ItemStack stack) {
        return stack.is(BLACKLISTED_FROM_RECYCLING);
    }

    /**
     * ES: Punto de entrada principal. Busca la receta que produjo este item probando,
     * en orden de prioridad: Stonecutter -> Hornos -> Crafting (shaped/shapeless/transmute)
     * -> Herrería. Devuelve null si no hay receta, si está en la blacklist, o si el item
     * tiene un DYED_COLOR (items teñidos no se reconstruyen a materiales).
     */
    public static RecipeMatch getRecipeMatch(ItemStack inputStack, Level level) {
        if (inputStack.isEmpty() || level.isClientSide() || level.getServer() == null) {
            return null;
        }

        if (isBlacklisted(inputStack)) {
            return null;
        }

        // ES: DEBUG TEMPORAL - trazar el estado exacto de camas/arneses antes de
        // cualquier corte temprano, para confirmar si cargan DYED_COLOR.
        String debugPath = BuiltInRegistries.ITEM.getKey(inputStack.getItem()).getPath();
        boolean isDebugTarget = debugPath.contains("bed") || debugPath.contains("harness");

        // ES: Items teñidos (armadura de cuero, etc.) no devuelven materiales al reciclar.
        // Si están encantados, el encantamiento se extrae por otra vía (ver processRecycling).
        DyedItemColor dyedColor = inputStack.get(DataComponents.DYED_COLOR);
        if (isDebugTarget) {
            LOGGER.info("[RecyclerTable DEBUG] getRecipeMatch para: " + debugPath
                    + " | DYED_COLOR=" + (dyedColor != null ? dyedColor.rgb() : "null")
                    + " | blacklisted=" + isBlacklisted(inputStack));
        }
        if (dyedColor != null) {
            return null;
        }

        RecipeManager recipeManager = level.getServer().getRecipeManager();
        return findByPriority(inputStack, recipeManager);
    }

    private static RecipeMatch findByPriority(ItemStack target, RecipeManager recipeManager) {
        RecipeMatch found;

        found = findInStonecutter(target, recipeManager);
        if (found != null) return found;

        found = findInCooking(target, recipeManager);
        if (found != null) return found;

        found = findInCrafting(target, recipeManager);
        if (found != null) return found;

        found = findInSmithing(target, recipeManager);
        if (found != null) return found;

        // ES: DEBUG TEMPORAL - si no se encontró nada y el item es cama/arnés,
        // volcar a consola qué recetas existen con ese nombre y de qué tipo/clase son.
        debugScanRecipes(target, recipeManager);

        return null;
    }

    /**
     * ES: DEBUG TEMPORAL - imprime en consola el tipo y clase real de cualquier
     * receta registrada cuyo ID contenga "bed" o "harness", para diagnosticar
     * por qué el reciclador no las encuentra. Borrar cuando el bug esté resuelto.
     */
    private static void debugScanRecipes(ItemStack target, RecipeManager recipeManager) {
        String path = BuiltInRegistries.ITEM.getKey(target.getItem()).getPath();
        if (!path.contains("bed") && !path.contains("harness")) {
            return;
        }

        LOGGER.info("[RecyclerTable DEBUG] Sin match para: " + path);
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            String id = holder.id().toString();
            if (id.contains("bed") || id.contains("harness")) {
                Recipe<?> recipe = holder.value();
                boolean ingredientsEmpty;
                try {
                    ingredientsEmpty = recipe.placementInfo().ingredients().isEmpty();
                } catch (Exception ex) {
                    ingredientsEmpty = true;
                }
                LOGGER.info("[RecyclerTable DEBUG]   id=" + id
                        + " | recipeType=" + recipe.getType()
                        + " | javaClass=" + recipe.getClass().getName()
                        + " | ingredientsEmpty=" + ingredientsEmpty);
            }
        }
    }

    /**
     * ES: Toma 1 muestra de cada ingrediente de la receta, EVITANDO elegir al propio
     * item objetivo como muestra cuando el ingrediente es una tag/lista amplia que
     * también lo acepta (ej. "cualquier color de cama/shulker/arnés/saco" al reteñir).
     * Esto es clave para recetas de "reteñido" (dye + item_de_cualquier_color ->
     * item_del_nuevo_color): sin esto, se elegiría al propio objetivo como su ingrediente,
     * causando duplicación o loops. Si el ingrediente de verdad SOLO acepta al objetivo
     * (loop real, sin alternativa), esa posición queda vacía (ItemStack.EMPTY) y el
     * llamador debe descartar la receta.
     */
    private static List<ItemStack> sampleFromExcluding(List<Ingredient> ingredients, Item excludeItem) {
        List<ItemStack> samples = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            var match = ingredient.items()
                    .filter(holder -> holder.value() != excludeItem)
                    .findFirst();
            samples.add(match.isPresent() ? new ItemStack(match.get().value()) : ItemStack.EMPTY);
        }
        return samples;
    }

    // ================= STONECUTTER =================
    private static RecipeMatch findInStonecutter(ItemStack target, RecipeManager recipeManager) {
        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.STONECUTTING)) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof StonecutterRecipe stonecutterRecipe)) continue;

            List<Ingredient> recipeIngredients = stonecutterRecipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> samples = sampleFromExcluding(recipeIngredients, target.getItem());
            if (samples.get(0).isEmpty()) continue;

            ItemStack output = stonecutterRecipe.assemble(new SingleRecipeInput(samples.get(0)));
            if (!output.isEmpty() && output.getItem() == target.getItem()) {
                List<ItemStack> result = new ArrayList<>();
                ItemStack copy = samples.get(0).copy();
                copy.setCount(1);
                result.add(copy);
                return new RecipeMatch(result, output.getCount());
            }
        }
        return null;
    }

        // ================= CRAFTING (genérico: shaped, shapeless, transmute, dyed, etc.) =================
    // ES: En vez de comprobar tipos concretos (ShapedRecipe, ShapelessRecipe, TransmuteRecipe...),
    // se maneja de forma genérica porque el juego sigue agregando nuevas subclases de receta de
    // crafteo (ej. "minecraft:crafting_dyed", usado para reteñir camas y arneses). Comprobar solo
    // tipos conocidos dejaba esas recetas invisibles para el reciclador. Aquí se intenta ensamblar
    // CUALQUIER receta registrada bajo RecipeType.CRAFTING usando su propio método assemble(),
    // sin importar su clase interna.
    @SuppressWarnings("unchecked")
    private static RecipeMatch findInCrafting(ItemStack target, RecipeManager recipeManager) {
        RecipeMatch fallbackDyedMatch = null;

        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.CRAFTING)) {
            Recipe<?> recipe = holder.value();

            List<Ingredient> recipeIngredients = recipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> samples = sampleFromExcluding(recipeIngredients, target.getItem());
            if (samples.stream().anyMatch(ItemStack::isEmpty)) continue;

            CraftingInput input = CraftingInput.of(samples.size(), 1, samples);

            ItemStack output;
            try {
                // ES: Cast genérico: toda receta bajo RecipeType.CRAFTING implementa
                // Recipe<CraftingInput>, sin importar la subclase concreta.
                output = ((Recipe<CraftingInput>) recipe).assemble(input);
            } catch (Exception ex) {
                // ES: Alguna receta especial puede lanzar excepción con un input sintético
                // que no coincide exactamente con lo que espera; se ignora y se sigue buscando.
                continue;
            }

            if (!output.isEmpty() && output.getItem() == target.getItem()) {
                List<ItemStack> result = new ArrayList<>();
                for (ItemStack sample : samples) {
                    ItemStack copy = sample.copy();
                    copy.setCount(1);
                    result.add(copy);
                }
                RecipeMatch match = new RecipeMatch(result, output.getCount());

                // ES: Si algún ingrediente de la receta es un TINTE (DyeItem), es una
                // receta de reteñido (ej. tinte + cama blanca -> cama roja), no de
                // materiales base reales. Esto reemplaza al chequeo anterior por clase de
                // Item del ingrediente (que fallaba porque, en esta versión, el item Harness
                // comparte la misma clase Java que el item Lana, dando falsos positivos).
                // Comparar por DyeItem es semánticamente correcto y no depende de detalles
                // internos de jerarquía de clases que pueden cambiar entre versiones.
                boolean referencesSameFamily = samples.stream()
                        .anyMatch(s -> s.getItem() instanceof net.minecraft.world.item.DyeItem);

                String debugPath = BuiltInRegistries.ITEM.getKey(target.getItem()).getPath();
                if (debugPath.contains("bed") || debugPath.contains("harness")) {
                    LOGGER.info("[RecyclerTable DEBUG] findInCrafting match para " + debugPath
                            + " | recipeId=" + holder.id()
                            + " | referencesSameFamily=" + referencesSameFamily
                            + " | ingredientes=" + result);
                }

                if (!referencesSameFamily) {
                    return match;
                } else if (fallbackDyedMatch == null) {
                    fallbackDyedMatch = match;
                }
            }
        }
        return fallbackDyedMatch;
    }
    // ================= HORNOS (smelting / blasting / smoking / campfire) =================
    private static RecipeMatch findInCooking(ItemStack target, RecipeManager recipeManager) {
        RecipeMatch result;

        result = searchCookingType(RecipeType.SMELTING, target, recipeManager);
        if (result != null) return result;

        result = searchCookingType(RecipeType.BLASTING, target, recipeManager);
        if (result != null) return result;

        result = searchCookingType(RecipeType.SMOKING, target, recipeManager);
        if (result != null) return result;

        result = searchCookingType(RecipeType.CAMPFIRE_COOKING, target, recipeManager);
        return result;
    }

    private static <T extends AbstractCookingRecipe> RecipeMatch searchCookingType(RecipeType<T> type, ItemStack target, RecipeManager recipeManager) {
        for (RecipeHolder<T> holder : recipeManager.recipeMap().byType(type)) {
            T recipe = holder.value();

            List<Ingredient> recipeIngredients = recipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> samples = sampleFromExcluding(recipeIngredients, target.getItem());
            if (samples.get(0).isEmpty()) continue;

            ItemStack output = recipe.assemble(new SingleRecipeInput(samples.get(0)));
            if (!output.isEmpty() && output.getItem() == target.getItem()) {
                List<ItemStack> result = new ArrayList<>();
                ItemStack copy = samples.get(0).copy();
                copy.setCount(1);
                result.add(copy);
                return new RecipeMatch(result, output.getCount());
            }
        }
        return null;
    }

    // ================= MESA DE HERRERÍA (solo smithing_transform) =================
    private static RecipeMatch findInSmithing(ItemStack target, RecipeManager recipeManager) {
        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.SMITHING)) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof SmithingTransformRecipe smithingRecipe)) continue;

            List<Ingredient> recipeIngredients = smithingRecipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> samples = sampleFromExcluding(recipeIngredients, target.getItem());
            ItemStack template = samples.size() > 0 ? samples.get(0) : ItemStack.EMPTY;
            ItemStack base = samples.size() > 1 ? samples.get(1) : ItemStack.EMPTY;
            ItemStack addition = samples.size() > 2 ? samples.get(2) : ItemStack.EMPTY;
            if (base.isEmpty()) continue;

            ItemStack output = smithingRecipe.assemble(new SmithingRecipeInput(template, base, addition));
            if (!output.isEmpty() && output.getItem() == target.getItem()) {
                List<ItemStack> result = new ArrayList<>();
                for (ItemStack sample : samples) {
                    if (sample.isEmpty()) continue;
                    ItemStack copy = sample.copy();
                    copy.setCount(1);
                    result.add(copy);
                }
                return new RecipeMatch(result, output.getCount());
            }
        }
        return null;
    }

    /**
     * ES: Procesa el stack COMPLETO del slot de proceso (puede tener varias unidades
     * acumuladas). Si el item está encantado, se procesa unidad por unidad (1 botella +
     * 1 libro por unidad). Si no, se calculan lotes según cuántas unidades pide la receta
     * original; el sobrante que no alcanza para un lote completo pasa sin convertir.
     */
    public static RecyclingOutput processRecycling(ItemStack stackInProcess, ItemStack emptyBottle, ItemStack book, Level level) {
        List<ItemStack> results = new ArrayList<>();

        if (stackInProcess.isEmpty() || level == null) {
            return new RecyclingOutput(results, 0, 0);
        }

        int totalCount = stackInProcess.getCount();
        ItemStack singleSample = stackInProcess.copyWithCount(1);

        // ES: Un enchanted_book guarda sus encantamientos en STORED_ENCHANTMENTS,
        // no en ENCHANTMENTS (ese componente es para items equipables encantados).
        boolean isBookSource = stackInProcess.is(Items.ENCHANTED_BOOK);
        ItemEnchantments enchantments = isBookSource
                ? stackInProcess.get(DataComponents.STORED_ENCHANTMENTS)
                : stackInProcess.get(DataComponents.ENCHANTMENTS);
        boolean isEnchanted = enchantments != null && !enchantments.isEmpty();

        if (isEnchanted) {
            boolean hasEmptyBottle = !emptyBottle.isEmpty();
            boolean hasBook = !book.isEmpty();

            if (!hasEmptyBottle || !hasBook) {
                results.add(stackInProcess.copy());
                return new RecyclingOutput(results, 0, 0);
            }

            // ES: Si la fuente es un libro encantado con N encantamientos, se necesita
            // 1 libro en blanco POR encantamiento (se separan en N libros individuales).
            // Si la fuente es un item equipable, solo se necesita 1 libro en blanco
            // (todos sus encantamientos se combinan en 1 solo libro de salida).
            int enchantCount = enchantments.entrySet().size();
            int booksNeededPerUnit = isBookSource ? Math.max(1, enchantCount) : 1;
            int bottlesNeededPerUnit = 1;

            int maxByBottles = emptyBottle.getCount() / bottlesNeededPerUnit;
            int maxByBooks = book.getCount() / booksNeededPerUnit;
            int processedUnits = Math.min(totalCount, Math.min(maxByBottles, maxByBooks));

            if (processedUnits <= 0) {
                results.add(stackInProcess.copy());
                return new RecyclingOutput(results, 0, 0);
            }

            // ES: Un enchanted_book no tiene receta de crafteo reconstruible; nunca
            // se devuelven materiales al reciclar uno.
            RecipeMatch match = isBookSource ? null : getRecipeMatch(singleSample, level);

            for (int unit = 0; unit < processedUnits; unit++) {
                if (!isBookSource && match != null) {
                    for (ItemStack ingredient : match.ingredients()) {
                        results.add(ingredient.copy());
                    }
                }

                if (isBookSource) {
                    createSeparateEnchantmentBooks(enchantments, results);
                } else {
                    results.add(createCombinedEnchantmentBook(enchantments));
                }

                results.add(new ItemStack(Items.EXPERIENCE_BOTTLE));
            }

            int leftover = totalCount - processedUnits;
            if (leftover > 0) {
                results.add(stackInProcess.copyWithCount(leftover));
            }

            int bottlesConsumed = processedUnits * bottlesNeededPerUnit;
            int booksConsumed = processedUnits * booksNeededPerUnit;
            return new RecyclingOutput(results, bottlesConsumed, booksConsumed);
        }

        RecipeMatch match = getRecipeMatch(singleSample, level);
        if (match == null || match.ingredients().isEmpty()) {
            results.add(stackInProcess.copy());
            return new RecyclingOutput(results, 0, 0);
        }

        int requiredQty = Math.max(1, match.outputCount());
        int batches = totalCount / requiredQty;
        int remainder = totalCount % requiredQty;

        if (batches <= 0) {
            results.add(stackInProcess.copy());
            return new RecyclingOutput(results, 0, 0);
        }

        for (ItemStack ingredient : match.ingredients()) {
            ItemStack copy = ingredient.copy();
            copy.setCount(ingredient.getCount() * batches);
            results.add(copy);
        }

        if (remainder > 0) {
            results.add(stackInProcess.copyWithCount(remainder));
        }

        return new RecyclingOutput(results, 0, 0);
    }

    /**
     * ES: Crea 1 solo libro encantado con TODOS los encantamientos de la fuente
     * (usado cuando la fuente reciclada NO es en sí misma un libro, ej. una espada).
     */
    private static ItemStack createCombinedEnchantmentBook(ItemEnchantments sourceEnchantments) {
        ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
        enchantedBook.set(DataComponents.STORED_ENCHANTMENTS, sourceEnchantments);
        return enchantedBook;
    }

    /**
     * ES: Separa cada encantamiento de la fuente en su propio libro encantado
     * (usado cuando la fuente reciclada YA es un enchanted_book con varios encantamientos).
     */
    private static void createSeparateEnchantmentBooks(ItemEnchantments sourceEnchantments, List<ItemStack> results) {
        if (sourceEnchantments != null && !sourceEnchantments.isEmpty()) {
            for (var entry : sourceEnchantments.entrySet()) {
                var enchantment = entry.getKey();
                int levelValue = entry.getIntValue();

                ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);

                ItemEnchantments.Mutable mutableEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mutableEnchantments.set(enchantment, levelValue);

                enchantedBook.set(DataComponents.STORED_ENCHANTMENTS, mutableEnchantments.toImmutable());
                results.add(enchantedBook);
            }
        }
    }
}
