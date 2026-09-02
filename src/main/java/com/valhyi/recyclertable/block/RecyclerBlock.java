package com.valhyi.recyclertable.block;

import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RecyclerBlockEntity(pos, state);
    }

    /**
     * Registra el BlockEntityTicker para que Minecraft ejecute automáticamente
     * el tick del RecyclerBlockEntity solo cuando está cargado
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Solo ejecutamos en el lado del servidor
        return level.isClientSide() ? null : createTickerHelper(type);
    }

    @Nullable
    private static <T extends BlockEntity> BlockEntityTicker<T> createTickerHelper(BlockEntityType<T> type) {
        return (lvl, pos, st, blockEntity) -> {
            if (blockEntity instanceof RecyclerBlockEntity recycler) {
                recycler.tick(lvl, pos, st);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            RecyclerBlockEntity recyclerBlockEntity = (RecyclerBlockEntity) level.getBlockEntity(pos);
            if (recyclerBlockEntity != null) {
                player.openMenu(recyclerBlockEntity, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            MenuProvider menuProvider = state.getMenuProvider(level, pos);
            if (menuProvider != null) {
                player.openMenu(menuProvider, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
