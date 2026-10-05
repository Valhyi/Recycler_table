package com.valhyi.recyclertable.recipe;

import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ES: Copia, del lado del CLIENTE, de las preferencias del servidor
 * (RecyclerPreferences). Se llena con RecyclerPreferencesSyncPayload y es lo
 * unico que lee RecyclerScreen: ya no se accede al servidor integrado, asi
 * que el panel funciona igual en singleplayer y en servidor dedicado.
 *
 * ES: IMPORTANTE: esta clase NO debe importar nada de net.minecraft.client.*,
 * porque ModNetworking (codigo comun) la referencia y tambien se carga en el
 * servidor dedicado.
 *
 * ES: Los mapas se reemplazan completos (copy-on-write) en vez de mutarse,
 * asi un render a mitad de actualizacion nunca ve un mapa a medias.
 *
 * ES: conflictsVersion / prefsVersion suben cada vez que llega un paquete (o
 * que el jugador cambia algo localmente); RecyclerScreen los compara en cada
 * tick para saber cuando reconstruir la lista de conflictos o refrescar la
 * seleccion si el menu ya estaba abierto.
 */
public final class ClientRecyclerData {

    private static volatile Map<Item, List<Item>> chosenVariants = Map.of();
    private static volatile Map<Item, Map<String, Item>> groupPreferences = Map.of();

    private static volatile int conflictsVersion = 0;
    private static volatile int prefsVersion = 0;

    private ClientRecyclerData() {}

    // ================= Actualizaciones desde el servidor =================

    public static void applyPreferences(Map<Item, List<Item>> chosen, Map<Item, Map<String, Item>> groups) {
        Map<Item, List<Item>> chosenCopy = new HashMap<>(chosen);

        Map<Item, Map<String, Item>> groupsCopy = new HashMap<>();
        for (Map.Entry<Item, Map<String, Item>> entry : groups.entrySet()) {
            groupsCopy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        chosenVariants = chosenCopy;
        groupPreferences = groupsCopy;
        prefsVersion++;
    }

    /** ES: Llamar despues de MultiRecipeScanner.applySynced. */
    public static void markConflictsUpdated() {
        conflictsVersion++;
    }

    // ================= Actualizacion optimista (al hacer click) =================

    public static void setPreferenceLocal(Item target, List<Item> signature) {
        Map<Item, List<Item>> copy = new HashMap<>(chosenVariants);
        copy.put(target, signature);
        chosenVariants = copy;
        prefsVersion++;
    }

    public static void setGroupPreferenceLocal(Item target, String groupKey, Item chosen) {
        Map<Item, Map<String, Item>> copy = new HashMap<>();
        for (Map.Entry<Item, Map<String, Item>> entry : groupPreferences.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        copy.computeIfAbsent(target, k -> new HashMap<>()).put(groupKey, chosen);
        groupPreferences = copy;
        prefsVersion++;
    }

    // ================= Lectura =================

    public static Optional<List<Item>> getPreference(Item target) {
        return Optional.ofNullable(chosenVariants.get(target));
    }

    public static Optional<Item> getGroupPreference(Item target, String groupKey) {
        Map<String, Item> perGroup = groupPreferences.get(target);
        if (perGroup == null) return Optional.empty();
        return Optional.ofNullable(perGroup.get(groupKey));
    }

    public static int getConflictsVersion() {
        return conflictsVersion;
    }

    public static int getPrefsVersion() {
        return prefsVersion;
    }
}
