package com.valhyi.recyclertable.network;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = RecyclerTable.MOD_ID)
public class ModNetworking {

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                RecyclerButtonPayload.TYPE,
                RecyclerButtonPayload.STREAM_CODEC,
                ModNetworking::handleButtonPacket
        );
    }

    private static void handleButtonPacket(RecyclerButtonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;

            var blockEntity = player.level().getBlockEntity(payload.pos());
            if (blockEntity instanceof RecyclerBlockEntity recycler) {
                switch (payload.button()) {
                    case PLAY -> recycler.triggerSingleShot();
                    case AUTO -> recycler.toggleAutoMode();
                }
            }
        });
    }
}
