package com.valhyi.recyclertable.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.valhyi.recyclertable.RecyclerTable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ES: Guarda, por item objetivo, qué "variante" de receta eligió el jugador
 * para el reciclaje (ver MultiRecipeScanner.RecipeVariant). La preferencia
 * es GLOBAL para el server (no por jugador), y se persiste con el mundo
 * usando el sistema SavedDataType introducido en 1.21.5.
 *
 * ES: Además del mapa de siempre (chosenVariants - una firma completa de
 * ingredientes por item), ahora también guarda groupPreferences: por cada
 * (item objetivo, clave de grupo de tag) - ver TagIngredientScanner.
 * IngredientGroup.key() / MultiRecipeScanner.VariantGroup.key() - qué UN
 * item eligió el jugador para ESE grupo en particular. Esto es lo que
 * permite que items con 2+ grupos sin material en común (ej. fogata: logs +
 * coals) tengan una elección independiente por grupo en vez de una sola
 * firma completa que no puede representar "cerezo Y carbón vegetal a la
 * vez" si esa combinación exacta no fue pre-generada como variante.
 * RecyclerLogic combina estas elecciones al momento de reciclar (ver
 * RecyclerLogic.findWithGroupPreferences). Este mapa es puramente aditivo:
 * no reemplaza chosenVariants, que se sigue usando igual que siempre para
 * los conflictos normales (una sola firma completa, ej. mossy_cobblestone).
 */
public class RecyclerPreferences extends SavedData {

    private static final Codec<Map<Item, List<Item>>> VARIANT_MAP_CODEC =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), BuiltInRegistries.ITEM.byNameCodec().listOf());

    private static final Codec<Map<String, Item>> GROUP_ITEM_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, BuiltInRegistries.ITEM.byNameCodec());

    private static final Codec<Map<Item, Map<String, Item>>> GROUP_PREF_MAP_CODEC =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), GROUP_ITEM_MAP_CODEC);

    /**
     * ES: Envoltorio puramente para (de)serializar ambos mapas juntos en un
     * solo Codec (SavedDataType espera un único tipo). No se usa fuera de
     * esta clase.
     */
    private record Data(Map<Item, List<Item>> chosenVariants, Map<Item, Map<String, Item>> groupPreferences) {}

    private static final Codec<Data> DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VARIANT_MAP_CODEC.optionalFieldOf("chosen_variants", Collections.emptyMap()).forGetter(Data::chosenVariants),
            GROUP_PREF_MAP_CODEC.optionalFieldOf("group_preferences", Collections.emptyMap()).forGetter(Data::groupPreferences)
    ).apply(instance, Data::new));

    public static final SavedDataType<RecyclerPreferences> TYPE = new SavedDataType<RecyclerPreferences>(
            RecyclerTable.resLoc("preferences"),
            ctx -> new RecyclerPreferences(new HashMap<>(), new HashMap<>()),
            ctx -> DATA_CODEC.xmap(
                    data -> new RecyclerPreferences(data.chosenVariants(), data.groupPreferences()),
                    RecyclerPreferences::toData
            ),
            DataFixTypes.LEVEL
    );

    private final Map<Item, List<Item>> chosenVariants;
    private final Map<Item, Map<String, Item>> groupPreferences;

    private RecyclerPreferences(Map<Item, List<Item>> chosenVariants, Map<Item, Map<String, Item>> groupPreferences) {
        // ES: El Codec puede entregar mapas inmutables al decodificar desde disco
        // (Codec.unboundedMap arma un ImmutableMap.Builder internamente). Si se
        // guardan tal cual, cualquier set*Preference() posterior explota con
        // UnsupportedOperationException. Envolver siempre en HashMap nuevos
        // (incluyendo los mapas internos de groupPreferences) garantiza que
        // queden mutables sin importar de dónde vengan.
        this.chosenVariants = new HashMap<>(chosenVariants);

        Map<Item, Map<String, Item>> mutableGroupPrefs = new HashMap<>();
        for (Map.Entry<Item, Map<String, Item>> entry : groupPreferences.entrySet()) {
            mutableGroupPrefs.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        this.groupPreferences = mutableGroupPrefs;
    }

    private Data toData() {
        return new Data(chosenVariants, groupPreferences);
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

    /**
     * ES: Devuelve el item que el jugador eligió para este grupo de tag en
     * particular (ej. target=campfire, groupKey="coals" -> charcoal), o
     * vacío si nunca se configuró (se usa el item base/por defecto del
     * grupo, comportamiento equivalente a no tener preferencia).
     */
    public Optional<Item> getGroupPreference(Item target, String groupKey) {
        Map<String, Item> perGroup = groupPreferences.get(target);
        if (perGroup == null) return Optional.empty();
        return Optional.ofNullable(perGroup.get(groupKey));
    }

    public void setGroupPreference(Item target, String groupKey, Item chosenItem) {
        groupPreferences.computeIfAbsent(target, k -> new HashMap<>()).put(groupKey, chosenItem);
        this.setDirty();
    }

    public void clearGroupPreference(Item target, String groupKey) {
        Map<String, Item> perGroup = groupPreferences.get(target);
        if (perGroup != null && perGroup.remove(groupKey) != null) {
            this.setDirty();
        }
    }
}
