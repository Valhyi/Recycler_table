package com.valhyi.recyclertable.recipe;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DataFixTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ES: Guarda, por item objetivo, qué "variante" de receta eligió el jugador
 * para el reciclaje (ver MultiRecipeScanner.RecipeVariant). La preferencia
 * es GLOBAL para el server (no por jugador), y se persiste con el mundo
 * usando el sistema SavedDataType introducido en 1.21.5.
 */
public class RecyclerPreferences extends SavedData {

    private static final Codec<Map<Item, List<Item>>> MAP_CODEC =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), BuiltInRegistries.ITEM.byNameCodec().listOf());

    public static final SavedDataType<RecyclerPreferences> TYPE = new SavedDataType<>(
            "recyclertable_preferences",
            ctx -> new RecyclerPreferences(new HashMap<>()),
            ctx -> MAP_CODEC.xmap(RecyclerPreferences::new, RecyclerPreferences::getRaw),
            DataFixTypes.LEVEL
    );

    private final Map<Item, List<Item>> chosenVariants;

    private RecyclerPreferences(Map<Item, List<Item>> chosenVariants) {
        this.chosenVariants = chosenVariants;
    }

    private Map<Item, List<Item>> getRaw() {
        return chosenVariants;
    }

    public static RecyclerPreferences get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * ES: Devuelve la firma de ingredientes (uno por slot, en orden) que el
     * jugador eligió para este item, o vacío si nunca se configuró (se usa
     * la primera coincidencia, comportamiento por defecto).
     */
    public Optional<List<Item>> getPreference(Item target) {
        return Optional.ofNullable(chosenVariants.get(target));
    }

    public void setPreference(Item target, List<Item> ingredientSignature) {
        chosenVariants.put(target, ingredientSignature);
        this.setDirty();
    }

    public void clearPreference(Item target) {
        if (chosenVariants.remove(target) != null) {
            this.setDirty();
        }
    }
}
