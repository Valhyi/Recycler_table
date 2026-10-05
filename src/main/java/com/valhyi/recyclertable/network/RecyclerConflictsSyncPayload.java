package com.valhyi.recyclertable.network;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.recipe.MultiRecipeScanner.RecipeVariant;
import com.valhyi.recyclertable.recipe.MultiRecipeScanner.VariantGroup;
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
 * ES: Servidor -> Cliente. Lleva el resultado del escaneo de recetas
 * multiples (MultiRecipeScanner) para que el cliente pueda dibujar el panel
 * de tags. El escaneo solo corre en el servidor (ServerStartedEvent); en un
 * servidor dedicado el cliente nunca lo ejecuta, asi que sin este paquete el
 * panel salia vacio. Se manda justo ANTES de abrir el menu (ver
 * ModNetworking.sendFullSync) para que RecyclerScreen.init() ya tenga los
 * datos.
 *
 * ES: La serializacion es manual (StreamCodec.of) porque la estructura es
 * anidada (mapas de listas de listas de items) y asi se evitan problemas de
 * inferencia de tipos. Las claves de grupo pueden pasar de 1100 caracteres,
 * por eso el limite del String es 32767 (igual que en
 * RecyclerGroupPreferencePayload).
 */
public record RecyclerConflictsSyncPayload(
        Map<Item, List<RecipeVariant>> items,
        Map<Item, List<VariantGroup>> groups
) implements CustomPacketPayload {

    public static final Type<RecyclerConflictsSyncPayload> TYPE =
            new Type<>(RecyclerTable.resLoc("recycler_conflicts_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM = ByteBufCodecs.registry(Registries.ITEM);

    // ES: Limites defensivos al leer (el servidor es de confianza, pero un
    // paquete corrupto no deberia poder pedir listas gigantes).
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_LIST = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, RecyclerConflictsSyncPayload> STREAM_CODEC =
            StreamCodec.of(RecyclerConflictsSyncPayload::write, RecyclerConflictsSyncPayload::read);

    // ================= ESCRITURA =================

    private static void write(RegistryFriendlyByteBuf buf, RecyclerConflictsSyncPayload payload) {
        buf.writeVarInt(payload.items.size());
        for (Map.Entry<Item, List<RecipeVariant>> entry : payload.items.entrySet()) {
            ITEM.encode(buf, entry.getKey());
            writeVariants(buf, entry.getValue());
        }

        buf.writeVarInt(payload.groups.size());
        for (Map.Entry<Item, List<VariantGroup>> entry : payload.groups.entrySet()) {
            ITEM.encode(buf, entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (VariantGroup group : entry.getValue()) {
                buf.writeUtf(group.key(), 32767);
                buf.writeVarInt(group.positions().size());
                for (int pos : group.positions()) {
                    buf.writeVarInt(pos);
                }
                writeVariants(buf, group.variants());
            }
        }
    }

    private static void writeVariants(RegistryFriendlyByteBuf buf, List<RecipeVariant> variants) {
        buf.writeVarInt(variants.size());
        for (RecipeVariant variant : variants) {
            buf.writeVarInt(variant.ingredientItems().size());
            for (Item item : variant.ingredientItems()) {
                ITEM.encode(buf, item);
            }
        }
    }

    // ================= LECTURA =================

    private static RecyclerConflictsSyncPayload read(RegistryFriendlyByteBuf buf) {
        int itemCount = checked(buf.readVarInt(), MAX_ENTRIES);
        Map<Item, List<RecipeVariant>> items = new HashMap<>();
        for (int i = 0; i < itemCount; i++) {
            Item target = ITEM.decode(buf);
            items.put(target, readVariants(buf));
        }

        int groupTargetCount = checked(buf.readVarInt(), MAX_ENTRIES);
        Map<Item, List<VariantGroup>> groups = new HashMap<>();
        for (int i = 0; i < groupTargetCount; i++) {
            Item target = ITEM.decode(buf);
            int groupCount = checked(buf.readVarInt(), MAX_LIST);
            List<VariantGroup> list = new ArrayList<>(groupCount);
            for (int g = 0; g < groupCount; g++) {
                String key = buf.readUtf(32767);
                int posCount = checked(buf.readVarInt(), MAX_LIST);
                List<Integer> positions = new ArrayList<>(posCount);
                for (int p = 0; p < posCount; p++) {
                    positions.add(buf.readVarInt());
                }
                list.add(new VariantGroup(key, positions, readVariants(buf)));
            }
            groups.put(target, list);
        }

        return new RecyclerConflictsSyncPayload(items, groups);
    }

    private static List<RecipeVariant> readVariants(RegistryFriendlyByteBuf buf) {
        int variantCount = checked(buf.readVarInt(), MAX_LIST);
        List<RecipeVariant> variants = new ArrayList<>(variantCount);
        for (int v = 0; v < variantCount; v++) {
            int size = checked(buf.readVarInt(), MAX_LIST);
            List<Item> signature = new ArrayList<>(size);
            for (int s = 0; s < size; s++) {
                signature.add(ITEM.decode(buf));
            }
            variants.add(new RecipeVariant(signature));
        }
        return variants;
    }

    private static int checked(int value, int max) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("Tamano invalido en RecyclerConflictsSyncPayload: " + value);
        }
        return value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
