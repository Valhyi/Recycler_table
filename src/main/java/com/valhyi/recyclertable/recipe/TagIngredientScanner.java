package com.valhyi.recyclertable.recipe;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ES: Detecta qué tags de item (ej. #minecraft:planks) se usan como
 * ingrediente en al menos una receta de crafting, para ofrecerlos en el
 * panel de tags (elegir qué item específico del tag preferir al reciclar).
 *
 * También mantiene un índice inverso (conjunto exacto de miembros -> tag)
 * que RecyclerLogic consulta en tiempo real para identificar a qué tag
 * corresponde cualquier ingrediente, sin depender de la estructura interna
 * de Ingredient (que cambia entre versiones) - solo compara qué items acepta
 * contra los tags realmente registrados.
 */
public class TagIngredientScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<Set<Item>, TagKey<Item>> tagsByMemberSet = Collections.emptyMap();
    private static Map<TagKey<Item>, List<Item>> usedTags = Collections.emptyMap();
    private static volatile boolean scanned = false;

    public static void scan(RecipeManager recipeManager) {
        Map<Set<Item>, TagKey<Item>> byMemberSet = new HashMap<>();

        BuiltInRegistries.ITEM.getTagNames().forEach(tagKey -> {
            Set<Item> members = BuiltInRegistries.ITEM.getTag(tagKey)
                    .map(holderSet -> holderSet.stream().map(Holder::value).collect(Collectors.toSet()))
                    .orElse(Collections.emptySet());
            if (members.size() >= 2) {
                byMemberSet.put(members, tagKey);
            }
        });
        tagsByMemberSet = byMemberSet;

        Map<TagKey<Item>, List<Item>> found = new HashMap<>();

        for (RecipeHolder<?> holder : recipeManager.recipeMap().byType(RecipeType.CRAFTING)) {
            Recipe<?> recipe = holder.value();

            List<Ingredient> ingredients;
            try {
                ingredients = recipe.placementInfo().ingredients();
            } catch (Exception ex) {
                continue;
            }

            for (Ingredient ingredient : ingredients) {
                Set<Item> items = ingredient.items().map(Holder::value).collect(Collectors.toSet());
                if (items.size() < 2) continue;

                TagKey<Item> tag = byMemberSet.get(items);
                if (tag == null) continue;

                found.computeIfAbsent(tag, k -> new ArrayList<>(items));
            }
        }

        usedTags = found;
        scanned = true;

        LOGGER.info("[RecyclerTable] Escaneo de tags de ingrediente completo: "
                + found.size() + " tag(s) usados en recetas");
        for (Map.Entry<TagKey<Item>, List<Item>> entry : found.entrySet()) {
            LOGGER.info("[RecyclerTable]   " + entry.getKey().location() + " -> " + entry.getValue());
        }
    }

    public static boolean isScanned() {
        return scanned;
    }

    public static Map<TagKey<Item>, List<Item>> getUsedTags() {
        return usedTags;
    }

    public static List<Item> getMembersFor(TagKey<Item> tag) {
        return usedTags.getOrDefault(tag, Collections.emptyList());
    }

    /**
     * ES: Dado el conjunto de items que acepta un ingrediente en tiempo real
     * (ingredient.items()), busca a qué tag corresponde exactamente. Null si
     * ningún tag registrado tiene ese mismo conjunto exacto de miembros.
     */
    public static TagKey<Item> findTagForItems(Set<Item> items) {
        return tagsByMemberSet.get(items);
    }
}
