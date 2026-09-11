package com.valhyi.recyclertable.network;

import com.valhyi.recyclertable.RecyclerTable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RecyclerButtonPayload(BlockPos pos, ButtonType button) implements CustomPacketPayload {

    public enum ButtonType {
        PLAY,
        AUTO
    }

    public static final Type<RecyclerButtonPayload> TYPE =
            new Type<>(RecyclerTable.resLoc("recycler_button"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecyclerButtonPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RecyclerButtonPayload::pos,
                    StreamCodec.of(
                            (buf, type) -> buf.writeEnum(type),
                            buf -> buf.readEnum(ButtonType.class)
                    ), RecyclerButtonPayload::button,
                    RecyclerButtonPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
