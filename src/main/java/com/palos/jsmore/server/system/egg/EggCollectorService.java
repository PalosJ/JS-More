package com.palos.jsmore.server.system.egg;

import com.palos.jsmore.server.block.entity.EggCollectorBlockEntity;
import com.palos.jsmore.server.registry.JSMoreItemTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class EggCollectorService {
    public static final int COLLECTION_RADIUS = 16;
    public static final int MINIMUM_ITEM_AGE_TICKS = 100;
    public static final int SCAN_INTERVAL_TICKS = 10;
    private static final double COLLECTION_RADIUS_SQUARED = COLLECTION_RADIUS * COLLECTION_RADIUS;

    private EggCollectorService() {
    }

    public static boolean shouldScan(long gameTime, BlockPos collectorPos) {
        long gamePhase = Math.floorMod(gameTime, SCAN_INTERVAL_TICKS);
        long positionPhase = Math.floorMod(collectorPos.asLong(), SCAN_INTERVAL_TICKS);
        return gamePhase == Math.floorMod(-positionPhase, SCAN_INTERVAL_TICKS);
    }

    public static boolean isWithinCollectionSphere(BlockPos collectorPos, Vec3 itemPosition) {
        return Vec3.atCenterOf(collectorPos).distanceToSqr(itemPosition) <= COLLECTION_RADIUS_SQUARED;
    }

    public static boolean isCollectible(ItemEntity itemEntity) {
        return itemEntity != null
                && !itemEntity.isRemoved()
                && itemEntity.getAge() >= MINIMUM_ITEM_AGE_TICKS
                && !itemEntity.getItem().isEmpty()
                && itemEntity.getItem().is(JSMoreItemTags.EGG_COLLECTOR_COLLECTIBLE_EGGS);
    }

    public static int collect(ServerLevel level, BlockPos collectorPos, EggCollectorBlockEntity collector) {
        IItemHandler inventory = collector.getItemHandler();
        if (!hasSpace(inventory)) {
            return 0;
        }
        Vec3 center = Vec3.atCenterOf(collectorPos);
        AABB bounds = new AABB(center, center).inflate(COLLECTION_RADIUS);
        List<ItemEntity> candidates = level.getEntitiesOfClass(
                ItemEntity.class,
                bounds,
                item -> isCollectible(item) && center.distanceToSqr(item.position()) <= COLLECTION_RADIUS_SQUARED
        );
        int collected = 0;
        for (ItemEntity itemEntity : candidates) {
            collected += transferToInventory(itemEntity, inventory);
        }
        return collected;
    }

    static boolean hasSpace(IItemHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    public static int transferToInventory(ItemEntity itemEntity, IItemHandler itemHandler) {
        if (!isCollectible(itemEntity) || itemHandler == null) {
            return 0;
        }
        ItemStack original = itemEntity.getItem();
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(itemHandler, original.copy(), false);
        int transferred = original.getCount() - remainder.getCount();
        if (transferred <= 0) {
            return 0;
        }
        if (remainder.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(remainder);
        }
        return transferred;
    }
}
