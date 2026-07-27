package com.palos.jsmore.server.block.entity;

import com.palos.jsmore.server.registry.JSMoreBlockEntityTypes;
import com.palos.jsmore.server.system.egg.EggCollectorService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public final class EggCollectorBlockEntity extends BaseContainerBlockEntity {
    public static final int CONTAINER_SIZE = 18;
    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final IItemHandler itemHandler = new InvWrapper(this);

    public EggCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(JSMoreBlockEntityTypes.EGG_COLLECTOR.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.jsmore.egg_collector");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x2, containerId, inventory, this, 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
    }

    public IItemHandler getItemHandler() {
        return this.itemHandler;
    }

    public static void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            EggCollectorBlockEntity collector
    ) {
        if (level instanceof ServerLevel serverLevel
                && EggCollectorService.shouldScan(serverLevel.getGameTime(), pos)) {
            EggCollectorService.collect(serverLevel, pos, collector);
        }
    }
}
