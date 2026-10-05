package com.valhyi.recyclertable.network;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import com.valhyi.recyclertable.gui.RecyclerMenu;
import com.valhyi.recyclertable.recipe.ClientRecyclerData;
import com.valhyi.recyclertable.recipe.MultiRecipeScanner;
import com.valhyi.recyclertable.recipe.RecyclerPreferences;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

@EventBusSubscriber(modid = RecyclerTable.MOD_ID)
public class ModNetworking {

    // ES: Distancia maxima (al cuadrado, en bloques) entre el jugador y la mesa
    // para aceptar un paquete de botones.
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // ---------------- Cliente -> Servidor ----------------
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

        // ---------------- Servidor -> Cliente (multijugador) ----------------
        registrar.playToClient(
                RecyclerConflictsSyncPayload.TYPE,
                RecyclerConflictsSyncPayload.STREAM_CODEC,
                ModNetworking::handleConflictsSync
        );

        registrar.playToClient(
                RecyclerPreferencesSyncPayload.TYPE,
                RecyclerPreferencesSyncPayload.STREAM_CODEC,
                ModNetworking::handlePreferencesSync
        );
    }

    // ================= ENVIO DE SINCRONIZACION (lado servidor) =================

    /**
     * ES: Manda al jugador TODO lo que RecyclerScreen necesita para dibujar
     * el panel de tags: conflictos de recetas + preferencias. Hay que
     * llamarlo ANTES de player.openMenu(...): los paquetes llegan en orden,
     * asi el cliente ya tiene los datos cuando RecyclerScreen.init() arma la
     * lista de conflictos.
     */
    public static void sendFullSync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new RecyclerConflictsSyncPayload(
                MultiRecipeScanner.getMultiRecipeItems(),
                MultiRecipeScanner.getUnlinkedGroupsMap()
        ));
        sendPreferences(player);
    }

    /** ES: Solo las preferencias (el mapa de conflictos no cambia). */
    public static void sendPreferences(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        RecyclerPreferences prefs = RecyclerPreferences.get(server);
        PacketDistributor.sendToPlayer(player, new RecyclerPreferencesSyncPayload(
                prefs.getChosenVariantsSnapshot(),
                prefs.getGroupPreferencesSnapshot()
        ));
    }

    /**
     * ES: Reenvia las preferencias a TODOS los jugadores que tengan una mesa
     * abierta. Las preferencias son globales del servidor, asi que si dos
     * jugadores tienen el panel abierto, ambos ven el cambio del otro.
     */
    private static void broadcastPreferences(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.containerMenu instanceof RecyclerMenu) {
                sendPreferences(other);
            }
        }
    }

    // ================= HANDLERS CLIENTE (llegan del servidor) =================
    // ES: Estos metodos solo tocan MultiRecipeScanner y ClientRecyclerData
    // (ninguno importa clases de net.minecraft.client), asi que es seguro que
    // vivan en codigo comun: no rompen el servidor dedicado.

    private static void handleConflictsSync(RecyclerConflictsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MultiRecipeScanner.applySynced(payload.items(), payload.groups());
            ClientRecyclerData.markConflictsUpdated();
        });
    }

    private static void handlePreferencesSync(RecyclerPreferencesSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                ClientRecyclerData.applyPreferences(payload.chosenVariants(), payload.groupPreferences())
        );
    }

    // ================= HANDLERS SERVIDOR (llegan del cliente) =================

    /**
     * ES: Devuelve la mesa de reciclaje SOLO si el jugador tiene abierto el
     * menu de ESA mesa y esta cerca. Un cliente modificado puede mandar
     * cualquier BlockPos; sin esta comprobacion podria accionar mesas ajenas
     * o forzar la carga de chunks lejanos.
     */
    private static RecyclerBlockEntity getOpenRecycler(ServerPlayer player, BlockPos pos) {
        if (!(player.containerMenu instanceof RecyclerMenu menu)) return null;
        if (!menu.getBlockPos().equals(pos)) return null;
        if (pos.distSqr(player.blockPosition()) > MAX_DISTANCE_SQR) return null;
        if (!(player.level().getBlockEntity(pos) instanceof RecyclerBlockEntity recycler)) return null;
        return recycler;
    }

    private static void handleButtonPacket(RecyclerButtonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            RecyclerBlockEntity recycler = getOpenRecycler(player, payload.pos());
            if (recycler == null) return;

            switch (payload.button()) {
                case PLAY -> recycler.triggerSingleShot();
                case AUTO -> recycler.toggleAutoMode();
            }
        });
    }

    /**
     * ES: El jugador eligio, desde el panel de tags, que variante de receta
     * usar para un item con recetas multiples. Se valida que tenga una mesa
     * abierta y que la firma sea una variante REAL de ese item (la lista
     * viene del escaneo del servidor, no del cliente). Tras guardar, se
     * reenvian las preferencias a todos los que tengan una mesa abierta.
     */
    private static void handlePreferencePacket(RecyclerPreferencePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof RecyclerMenu)) return;

            Item target = payload.target();
            List<Item> signature = payload.ingredientSignature();

            boolean valid = MultiRecipeScanner.getVariantsFor(target).stream()
                    .anyMatch(variant -> variant.ingredientItems().equals(signature));
            if (!valid) return;

            MinecraftServer server = player.level().getServer();
            RecyclerPreferences prefs = RecyclerPreferences.get(server);
            prefs.setPreference(target, signature);
            broadcastPreferences(server);
        });
    }

    /**
     * ES: El jugador eligio, desde el panel de tags, que item usar para UN
     * grupo de tag de un item con 2+ grupos sin material en comun (ej.
     * fogata). Se valida que el grupo exista para ese item y que el item
     * elegido sea una opcion real de ese grupo. Tras guardar, se reenvian las
     * preferencias a todos los que tengan una mesa abierta.
     */
    private static void handleGroupPreferencePacket(RecyclerGroupPreferencePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof RecyclerMenu)) return;

            Item target = payload.target();
            String groupKey = payload.groupKey();
            Item chosen = payload.chosenItem();

            boolean valid = MultiRecipeScanner.getUnlinkedGroupsFor(target).stream()
                    .filter(group -> group.key().equals(groupKey))
                    .anyMatch(group -> groupOffers(group, chosen));
            if (!valid) return;

            MinecraftServer server = player.level().getServer();
            RecyclerPreferences prefs = RecyclerPreferences.get(server);
            prefs.setGroupPreference(target, groupKey, chosen);
            broadcastPreferences(server);
        });
    }

    /**
     * ES: true si alguna variante del grupo usa "item" en la posicion que
     * ese grupo controla.
     */
    private static boolean groupOffers(MultiRecipeScanner.VariantGroup group, Item item) {
        if (group.positions().isEmpty()) return false;
        int pos = group.positions().get(0);

        for (MultiRecipeScanner.RecipeVariant variant : group.variants()) {
            List<Item> signature = variant.ingredientItems();
            if (pos >= 0 && pos < signature.size() && signature.get(pos) == item) {
                return true;
            }
        }
        return false;
    }
}
