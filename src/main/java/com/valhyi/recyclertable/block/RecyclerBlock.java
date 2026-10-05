package com.valhyi.recyclertable.block;

import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import com.valhyi.recyclertable.init.ModBlockEntities;
import com.valhyi.recyclertable.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class RecyclerBlock extends Block implements EntityBlock {
    public RecyclerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RecyclerBlockEntity(pos, state);
    }

    /**
     * Registra el BlockEntityTicker para ejecutar automáticamente
     * el tick del RecyclerBlockEntity cada tick del servidor
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        // Solo en servidor
        if (level.isClientSide()) {
            return null;
        }

        // Verificar que sea el tipo correcto
        if (blockEntityType != ModBlockEntities.RECYCLER_BLOCK_ENTITY.get()) {
            return null;
        }

        // Cast seguro y retorno del ticker
        return (lvl, pos, st, entity) -> {
            if (entity instanceof RecyclerBlockEntity recycler) {
                RecyclerBlockEntity.serverTick(lvl, pos, st, recycler);
            }
        };
    }

    /**
     * ES: Abre el menu. No hace falta sobreescribir useItemOn: el
     * comportamiento por defecto (TRY_WITH_EMPTY_HAND) ya cae aqui con la
     * mano principal, tenga o no un item.
     *
     * ES: MULTIJUGADOR: antes de abrir el menu se manda al jugador la
     * sincronizacion del panel de tags (conflictos + preferencias). Tiene que
     * ir ANTES de openMenu porque los paquetes llegan en orden y
     * RecyclerScreen.init() arma la lista de conflictos al crearse.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MenuProvider menuProvider) {
                if (player instanceof ServerPlayer serverPlayer) {
                    ModNetworking.sendFullSync(serverPlayer);
                }
                player.openMenu(menuProvider, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
