package com.valhyi.recyclertable.network;

import com.valhyi.recyclertable.RecyclerTable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES: Servidor -> Cliente. Copia completa de RecyclerPreferences (firmas
 * elegidas por item + elecciones por grupo) para que RecyclerScreen pueda
 * dibujar los iconos de preferencia y la seleccion verde sin acceder al
 * servidor (en multijugador getSingleplayerServer() es null).
 *
 * ES: Se manda al abrir la mesa y cada vez que alguien cambia una preferencia
 * (a todos los jugadores que tengan una mesa abierta). Es una copia completa
 * y no un diff porque el mapa es chico y asi el cliente nunca se desincroniza.
 */
public record RecyclerPreferencesSyncPayload(
        Map<Item, List<Item>> chosenVariants,
        Map<Item, Map<String, Item>> groupPreferences
) implements CustomPacketPayload {

    public static final Type<RecyclerPreferencesSyncPayload> TYPE =
            new Type<>(RecyclerTable.resLoc("recycler_preferences_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM = ByteBufCodecs.registry(Registries.ITEM);

    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_LIST = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, RecyclerPreferencesSyncPayload> STREAM_CODEC =
            StreamCodec.of(RecyclerPreferencesSyncPayload::write, RecyclerPreferencesSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, RecyclerPreferencesSyncPayload payload) {
        buf.writeVarInt(payload.chosenVariants.size());
        for (Map.Entry<Item, List<Item>> entry : payload.chosenVariants.entrySet()) {
            ITEM.encode(buf, entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (Item item : entry.getValue()) {
                ITEM.encode(buf, item);
            }
        }

        buf.writeVarInt(payload.groupPreferences.size());
        for (Map.Entry<Item, Map<String, Item>> entry : payload.groupPreferences.entrySet()) {
            ITEM.encode(buf, entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (Map.Entry<String, Item> groupEntry : entry.getValue().entrySet()) {
                buf.writeUtf(groupEntry.getKey(), 32767);
                ITEM.encode(buf, groupEntry.getValue());
            }
        }
    }

    private static RecyclerPreferencesSyncPayload read(RegistryFriendlyByteBuf buf) {
        int variantCount = checked(buf.readVarInt(), MAX_ENTRIES);
        Map<Item, List<Item>> chosen = new HashMap<>();
        for (int i = 0; i < variantCount; i++) {
            Item target = ITEM.decode(buf);
            int size = checked(buf.readVarInt(), MAX_LIST);
            List<Item> signature = new ArrayList<>(size);
            for (int s = 0; s < size; s++) {
                signature.add(ITEM.decode(buf));
            }
            chosen.put(target, signature);
        }

        int groupTargetCount = checked(buf.readVarInt(), MAX_ENTRIES);
        Map<Item, Map<String, Item>> groups = new HashMap<>();
        for (int i = 0; i < groupTargetCount; i++) {
            Item target = ITEM.decode(buf);
            int groupCount = checked(buf.readVarInt(), MAX_LIST);
            Map<String, Item> perGroup = new HashMap<>();
            for (int g = 0; g < groupCount; g++) {
                String key = buf.readUtf(32767);
                perGroup.put(key, ITEM.decode(buf));
            }
            groups.put(target, perGroup);
        }

        return new RecyclerPreferencesSyncPayload(chosen, groups);
    }

    private static int checked(int value, int max) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("Tamano invalido en RecyclerPreferencesSyncPayload: " + value);
        }
        return value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
