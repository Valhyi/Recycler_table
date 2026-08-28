package com.valhyi.recyclertable;

import com.valhyi.recyclertable.init.ModBlockEntities;
import com.valhyi.recyclertable.init.ModBlocks;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// ES: Clase principal del mod cargada por NeoForge 26.2
// EN: Main mod class loaded by NeoForge 26.2
@Mod(RecyclerTable.MOD_ID)
public class RecyclerTable {
    // ES: Identificador único del mod
    // EN: Unique mod identifier
    public static final String MOD_ID = "recyclertable";
    public static final Logger LOGGER = LogManager.getLogger();

    public RecyclerTable(IEventBus modEventBus) {
        LOGGER.info("ES: Inicializando Recycler Table mod | EN: Initializing Recycler Table mod");

        // ES: Registrar los componentes en el bus de eventos del mod
        // EN: Register components to the mod event bus
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
    }
}
