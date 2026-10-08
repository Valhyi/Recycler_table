package com.valhyi.recyclertable.event;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import com.valhyi.recyclertable.init.ModBlockEntities;
import com.valhyi.recyclertable.recipe.MultiRecipeScanner;
import com.valhyi.recyclertable.recipe.RecyclerLogic;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Registers capabilities for hopper and pipe interaction with the Recycler Table,
 * runs the one-time multi-recipe scan used by the tags panel, and frees the
 * recipe cache when the server stops.
 */
@EventBusSubscriber(modid = RecyclerTable.MOD_ID)
public class CommonEvents {

    @SubscribeEvent
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Register Item capability for hopper interaction
        // This allows hoppers, pipes, and other automation to extract/insert items
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            ModBlockEntities.RECYCLER_BLOCK_ENTITY.get(),
            RecyclerBlockEntity::getCapability
        );
    }

    /**
     * ES: Corre UNA SOLA VEZ al iniciar el server, cuando el RecipeManager ya
     * tiene todas las recetas cargadas (mismo momento en que JEI arma su
     * caché de recetas). Ver MultiRecipeScanner para más detalle.
     */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        MultiRecipeScanner.scan(event.getServer().getRecipeManager());
    }

    /**
     * ES: Libera el caché de recetas de RecyclerLogic al cerrar el server
     * (singleplayer: al salir del mundo). El caché ya se invalida solo en un
     * /reload (ver RecyclerLogic.validateCache); esto solo suelta la memoria.
     */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        RecyclerLogic.clearCache();
    }
}
