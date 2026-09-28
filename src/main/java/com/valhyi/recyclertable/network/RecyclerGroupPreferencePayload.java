package com.valhyi.recyclertable.network;

import com.valhyi.recyclertable.RecyclerTable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/**
 * ES: Cliente -> Servidor. El jugador eligió, para el item "target" y el
 * grupo de tag "groupKey" (ver TagIngredientScanner.IngredientGroup.key() /
 * MultiRecipeScanner.VariantGroup.key()), usar "chosenItem" para ESE grupo
 * en particular. A diferencia de RecyclerPreferencePayload (que guarda una
 * firma completa de ingredientes), este payload guarda una elección
 * independiente por grupo - necesario para items con 2+ grupos sin material
 * en común (ej. fogata: logs + coals), donde el jugador puede combinar
 * "cerezo" para logs y "carbón vegetal" para coals en una sola unidad.
 * Ver RecyclerPreferences.setGroupPreference y
 * RecyclerLogic.findWithGroupPreferences.
 *
 * ES: El límite de la clave del grupo es 32767 (máximo de Minecraft para un
 * String) porque la clave canónica de grupos grandes (ej. todos los troncos,
 * maderas y tallos) supera los 1100 caracteres. Con 1024 el paquete fallaba
 * al codificarse y desconectaba al jugador.
 */
public record RecyclerGroupPreferencePayload(Item target, String groupKey, Item chosenItem) implements CustomPacketPayload {

    public static final Type<RecyclerGroupPreferencePayload> TYPE =
            new Type<>(RecyclerTable.resLoc("recycler_group_preference"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecyclerGroupPreferencePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(Registries.ITEM), RecyclerGroupPreferencePayload::target,
                    ByteBufCodecs.stringUtf8(32767), RecyclerGroupPreferencePayload::groupKey,
                    ByteBufCodecs.registry(Registries.ITEM), RecyclerGroupPreferencePayload::chosenItem,
                    RecyclerGroupPreferencePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
