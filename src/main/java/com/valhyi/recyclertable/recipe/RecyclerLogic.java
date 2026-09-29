package com.valhyi.recyclertable.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.HoneycombItem;
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
import net.minecraft.world.level.block.Block;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
     * ES: Contraparte SIN encerar de un item "encerado" (ej.
     * waxed_copper_grate -> copper_grate), usando el mismo mapa vanilla que
     * usa el juego para el clic derecho con panal de cera sobre cobre
     * (HoneycombItem.WAX_OFF_BY_BLOCK - Supplier<BiMap<Block, Block>>,
     * encerado -> sin encerar; no existe un método getUnwaxed(Block), solo
     * el campo). Se prefiere esto a comparar por nombre (prefijo "waxed_")
     * porque cubre automáticamente TODAS las variantes de cobre (bloque,
     * expuesto, curtido, oxidado, puertas, trampillas, rejillas, talladas,
     * cortadas, escaleras, losas, etc.) sin tener que enumerarlas a mano, y
     * sigue funcionando si el datapack agrega más.
     */
    public static Optional<Item> getUnwaxedCounterpart(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return Optional.empty();
        }
        Block waxedBlock = blockItem.getBlock();
        Block unwaxedBlock = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(waxedBlock);
        return Optional.ofNullable(unwaxedBlock).map(Block::asItem);
    }

    /**
     * ES: true si este item es la versión ENCERADA de algo (tiene
     * contraparte sin encerar). Usado por MultiRecipeScanner para excluir
     * estos items del panel de conflictos: la receta a usar no es una
     * elección real del jugador, SIEMPRE es panal + contraparte sin encerar
     * (ver findWaxedMatch), nunca cualquier otra receta de crafteo que
     * también produzca el mismo bloque.
     */
    public static boolean isWaxedItem(Item item) {
        return getUnwaxedCounterpart(item).isPresent();
    }

    /**
     * ES: Receta SINTÉTICA y forzada para items encerados: 1 panal de miel +
     * 1 unidad de la contraparte sin encerar. Se arma directo, sin buscar en
     * el RecipeManager, porque en esta versión algunos bloques de cobre
     * tienen UNA SEGUNDA receta de crafting que compite (bloques de cobre en
     * crudo -> directamente la versión encerada - ver captura del usuario).
     * findInCrafting no puede distinguir cuál es "la correcta": elige la
     * primera que encuentra según el orden del RecipeManager, que no está
     * garantizado, y a veces terminaba devolviendo bloques de cobre en vez
     * de panal + item sin encerar. Esto se resuelve devolviendo esta receta
     * directo, con máxima prioridad (ver findByPriority), sin ambigüedad.
     */
    private static RecipeMatch findWaxedMatch(ItemStack target) {
        Optional<Item> unwaxed = getUnwaxedCounterpart(target.getItem());
        if (unwaxed.isEmpty()) {
            return null;
        }

        List<ItemStack> ingredients = new ArrayList<>();
        ingredients.add(new ItemStack(Items.HONEYCOMB));
        ingredients.add(new ItemStack(unwaxed.get()));
        return new RecipeMatch(ingredients, 1);
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

        // ES: Items teñidos (armadura de cuero, etc.) no devuelven materiales al reciclar.
        // Si están encantados, el encantamiento se extrae por otra vía (ver processRecycling).
        DyedItemColor dyedColor = inputStack.get(DataComponents.DYED_COLOR);
        if (dyedColor != null) {
            return null;
        }

        RecipeManager recipeManager = level.getServer().getRecipeManager();
        return findByPriority(inputStack, recipeManager, level);
    }

    private static RecipeMatch findByPriority(ItemStack target, RecipeManager recipeManager, Level level) {
        // ES: Items encerados (waxed_*) SIEMPRE se reciclan con panal +
        // contraparte sin encerar, sin importar qué otras recetas de
        // crafteo existan para el mismo bloque (ver findWaxedMatch). Máxima
        // prioridad: se resuelve antes que cualquier otro caso, incluidos
        // los grupos independientes.
        RecipeMatch waxedMatch = findWaxedMatch(target);
        if (waxedMatch != null) {
            return waxedMatch;
        }

        // ES: Caso especial - items cuya (única) receta de crafting tiene 2+
        // grupos de tag SIN material en común (ej. fogata: logs + coals; ver
        // MultiRecipeScanner.hasUnlinkedGroups). Para estos, el jugador puede
        // haber configurado una preferencia INDEPENDIENTE por cada grupo desde
        // el panel de tags (ver RecyclerPreferences.getGroupPreference), algo
        // que el resto de findInCrafting no puede resolver porque solo conoce
        // variantes que cambian UN grupo a la vez respecto a la base (nunca
        // "cerezo Y carbón vegetal simultáneamente" si esa combinación exacta
        // no fue pre-generada). Se resuelve aparte, combinando las elecciones
        // guardadas directamente sobre la receta real, antes de intentar el
        // resto de las prioridades normales.
        if (MultiRecipeScanner.hasUnlinkedGroups(target.getItem())) {
            RecipeMatch combined = findWithGroupPreferences(target, recipeManager, level);
            if (combined != null) return combined;
        }

        RecipeMatch found;

        found = findInStonecutter(target, recipeManager);
        if (found != null) return found;

        found = findInCooking(target, recipeManager);
        if (found != null) return found;

        found = findInCrafting(target, recipeManager, level);
        if (found != null) return found;

        found = findInSmithing(target, recipeManager);
        if (found != null) return found;

        return null;
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

    /**
     * ES: Resuelve el caso "fogata": una receta con 2+ grupos de tag sin
     * material en común, combinando la preferencia guardada de CADA grupo
     * (RecyclerPreferences.getGroupPreference) sobre la muestra base de la
     * receta real. Un grupo sin preferencia guardada conserva su item base
     * (el mismo comportamiento que si nunca se hubiera tocado el panel de
     * tags). Se re-ensambla la receta con la combinación final para
     * confirmar que sigue produciendo el item objetivo antes de devolverla.
     *
     * Busca sobre TODAS las recetas de crafting (no solo la "esperada") por
     * si en el futuro más de una receta produce el mismo target con grupos
     * propios; en la práctica hoy es siempre una sola.
     */
    @SuppressWarnings("unchecked")
    private static RecipeMatch findWithGroupPreferences(ItemStack target, RecipeManager recipeManager, Level level) {
        if (level == null || level.getServer() == null) return null;

        RecyclerPreferences prefs = RecyclerPreferences.get(level.getServer());

        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.CRAFTING)) {
            Recipe<?> recipe = holder.value();

            List<Ingredient> recipeIngredients = recipe.placementInfo().ingredients();
            if (recipeIngredients.isEmpty()) continue;

            List<ItemStack> baseSamples = sampleFromExcluding(recipeIngredients, target.getItem());
            if (baseSamples.stream().anyMatch(ItemStack::isEmpty)) continue;

            ItemStack baseOutput;
            try {
                baseOutput = ((Recipe<CraftingInput>) recipe).assemble(CraftingInput.of(baseSamples.size(), 1, baseSamples));
            } catch (Exception ex) {
                continue;
            }
            if (baseOutput.isEmpty() || baseOutput.getItem() != target.getItem()) continue;

            List<TagIngredientScanner.IngredientGroup> groups = TagIngredientScanner.detectGroups(recipeIngredients);
            if (groups.size() < 2 || TagIngredientScanner.groupsShareMaterial(groups)) {
                // ES: Esta receta en particular no es del caso "grupos sin
                // material en común" - no aplica aquí, se sigue buscando (o
                // se descarta si no hay más recetas de este target).
                continue;
            }

            List<ItemStack> finalSamples = new ArrayList<>(baseSamples.size());
            for (ItemStack sample : baseSamples) {
                finalSamples.add(sample.copy());
            }

            for (TagIngredientScanner.IngredientGroup group : groups) {
                Optional<Item> preferred = prefs.getGroupPreference(target.getItem(), group.key());
                if (preferred.isEmpty()) continue;

                Item chosen = preferred.get();
                // ES: Ignorar preferencias inválidas (ej. el datapack cambió y
                // ese item ya no pertenece al grupo, o apunta al propio
                // objetivo) en vez de romper el reciclaje: se conserva el
                // item base de ese grupo para esta unidad.
                if (chosen == target.getItem() || !group.items().contains(chosen)) continue;

                for (int pos : group.positions()) {
                    finalSamples.set(pos, new ItemStack(chosen));
                }
            }

            ItemStack finalOutput;
            try {
                finalOutput = ((Recipe<CraftingInput>) recipe).assemble(CraftingInput.of(finalSamples.size(), 1, finalSamples));
            } catch (Exception ex) {
                continue;
            }
            if (finalOutput.isEmpty() || finalOutput.getItem() != target.getItem()) continue;

            List<ItemStack> result = new ArrayList<>();
            for (ItemStack sample : finalSamples) {
                ItemStack copy = sample.copy();
                copy.setCount(1);
                result.add(copy);
            }
            return new RecipeMatch(result, finalOutput.getCount());
        }

        return null;
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
    //
    // ES: Junta TODAS las coincidencias reales (no de reteñido) en vez de devolver la
    // primera. Si el jugador configuró una preferencia en el panel de tags para este
    // item (ver RecyclerPreferences), se usa esa; si no, se mantiene el comportamiento
    // anterior (primera coincidencia encontrada). Además, si algún ingrediente es un tag
    // con varios items posibles (ej. "planks"), se agrega una coincidencia extra por cada
    // item del tag (ver TagIngredientScanner.expandGroupedVariants) para que la preferencia
    // elegida en el panel tenga con qué coincidir durante el reciclado real.
    //
    // ES: Items encerados (waxed_*) ya NO llegan hasta acá - se resuelven antes, en
    // findByPriority, vía findWaxedMatch. Esto evita justamente la ambigüedad que
    // tenían (2 recetas de crafting válidas para el mismo bloque: panal, y bloques de
    // cobre en crudo) sin depender de qué reciba primero el RecipeManager.
    @SuppressWarnings("unchecked")
    private static RecipeMatch findInCrafting(ItemStack target, RecipeManager recipeManager, Level level) {
        RecipeMatch fallbackDyedMatch = null;
        List<RecipeMatch> realMatches = new ArrayList<>();

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

                if (!referencesSameFamily) {
                    realMatches.add(match);

                    // ES: Ingredientes por tag con varios items posibles (ej. "planks" en la
                    // mesa de crafteo o en una cama): una coincidencia extra por cada item del
                    // tag, sustituyendo TODAS las posiciones que comparten ese tag a la vez.
                    for (List<ItemStack> variantSamples : TagIngredientScanner.expandGroupedVariants(recipeIngredients, samples, target.getItem())) {
                        ItemStack variantOutput;
                        try {
                            variantOutput = ((Recipe<CraftingInput>) recipe).assemble(CraftingInput.of(variantSamples.size(), 1, variantSamples));
                        } catch (Exception ex) {
                            continue;
                        }
                        if (variantOutput.isEmpty() || variantOutput.getItem() != target.getItem()) continue;

                        List<ItemStack> variantResult = new ArrayList<>();
                        for (ItemStack sample : variantSamples) {
                            ItemStack copy = sample.copy();
                            copy.setCount(1);
                            variantResult.add(copy);
                        }
                        realMatches.add(new RecipeMatch(variantResult, variantOutput.getCount()));
                    }
                } else if (fallbackDyedMatch == null) {
                    fallbackDyedMatch = match;
                }
            }
        }

        if (!realMatches.isEmpty()) {
            RecipeMatch preferred = applyPreference(target.getItem(), realMatches, level);
            return preferred != null ? preferred : realMatches.get(0);
        }
        return fallbackDyedMatch;
    }

    /**
     * ES: Si el jugador configuró en el panel de tags cuál receta prefiere para
     * este item (ver RecyclerPreferences), busca entre las coincidencias reales
     * la que tenga esa misma firma de ingredientes (mismos items, mismo orden).
     * Si no hay preferencia guardada, o el server no está disponible, devuelve
     * null y el llamador usa la primera coincidencia (comportamiento anterior).
     */
    private static RecipeMatch applyPreference(Item target, List<RecipeMatch> candidates, Level level) {
        if (level == null || level.getServer() == null) return null;

        RecyclerPreferences prefs = RecyclerPreferences.get(level.getServer());
        Optional<List<Item>> preferred = prefs.getPreference(target);
        if (preferred.isEmpty()) return null;

        List<Item> wanted = preferred.get();
        for (RecipeMatch candidate : candidates) {
            List<Item> signature = candidate.ingredients().stream().map(ItemStack::getItem).toList();
            if (signature.equals(wanted)) {
                return candidate;
            }
        }
        return null;
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
