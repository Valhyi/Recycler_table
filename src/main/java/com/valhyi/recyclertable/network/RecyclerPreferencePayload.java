package com.valhyi.recyclertable.network;

import com.valhyi.recyclertable.RecyclerTable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * ES: Cliente -> Servidor. El jugador eligió, para el item "target", usar la
 * receta cuya firma de ingredientes es "ingredientSignature" (ver
 * MultiRecipeScanner.RecipeVariant / RecyclerPreferences).
 */
public record RecyclerPreferencePayload(Item target, List<Item> ingredientSignature) implements CustomPacketPayload {

    public static final Type<RecyclerPreferencePayload> TYPE =
            new Type<>(RecyclerTable.resLoc("recycler_preference"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecyclerPreferencePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(Registries.ITEM), RecyclerPreferencePayload::target,
                    ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list()), RecyclerPreferencePayload::ingredientSignature,
                    RecyclerPreferencePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
