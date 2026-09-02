package com.valhyi.recyclertable.block;

import com.valhyi.recyclertable.block.entity.HopperIntegration;
import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof MenuProvider ? (MenuProvider) blockEntity : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            
            if (blockEntity instanceof RecyclerBlockEntity recyclerBlockEntity) {
                player.openMenu(recyclerBlockEntity, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MenuProvider menuProvider = this.getMenuProvider(state, level, pos);
            if (menuProvider != null) {
                serverPlayer.openMenu(menuProvider, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Soporte para tolvas (Hoppers)
     * Permite insertar items en el input grid (slots 0-8)
     */
    public boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }

    @Nullable
    public static BlockEntity getBlockEntityForHopper(Level level, BlockPos pos) {
        return level.getBlockEntity(pos);
    }
}
