package com.valhyi.recyclertable.network;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import com.valhyi.recyclertable.recipe.RecyclerPreferences;
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

        registrar.playToServer(
                RecyclerPreferencePayload.TYPE,
                RecyclerPreferencePayload.STREAM_CODEC,
                ModNetworking::handlePreferencePacket
        );

        registrar.playToServer(
                RecyclerGroupPreferencePayload.TYPE,
                RecyclerGroupPreferencePayload.STREAM_CODEC,
                ModNetworking::handleGroupPreferencePacket
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

    /**
     * ES: El jugador eligió, desde el panel de tags, qué variante de receta
     * usar para un item con recetas múltiples. Guarda la preferencia global
     * (ver RecyclerPreferences); RecyclerLogic la consulta en el próximo
     * reciclaje de ese item.
     */
    private static void handlePreferencePacket(RecyclerPreferencePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;

            RecyclerPreferences prefs = RecyclerPreferences.get(player.level().getServer());
            prefs.setPreference(payload.target(), payload.ingredientSignature());
        });
    }

    /**
     * ES: El jugador eligió, desde el panel de tags, qué item usar para UN
     * grupo de tag en particular de un item con 2+ grupos sin material en
     * común (ej. fogata: logs + coals). Guarda la preferencia de ESE grupo
     * nada más (ver RecyclerPreferences.setGroupPreference);
     * RecyclerLogic.findWithGroupPreferences la combina con la de los
     * demás grupos en el próximo reciclaje de ese item.
     */
    private static void handleGroupPreferencePacket(RecyclerGroupPreferencePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;

            RecyclerPreferences prefs = RecyclerPreferences.get(player.level().getServer());
            prefs.setGroupPreference(payload.target(), payload.groupKey(), payload.chosenItem());
        });
    }
}
