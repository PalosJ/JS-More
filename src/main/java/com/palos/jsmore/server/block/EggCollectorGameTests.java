package com.palos.jsmore.server.block;

import com.palos.jsmore.JSMore;
import com.palos.jsmore.server.block.entity.EggCollectorBlockEntity;
import com.palos.jsmore.server.registry.JSMoreBlocks;
import com.palos.jsmore.server.registry.JSMoreItemTags;
import com.palos.jsmore.server.registry.JSMoreItems;
import com.palos.jsmore.server.system.egg.EggCollectorService;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EggCollectorGameTests {
    private static final List<ResourceLocation> APPROVED_BASE_EGGS = List.of(
            ResourceLocation.withDefaultNamespace("egg"),
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "egg_alligator"),
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "egg_ostrich"),
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "egg_frog"),
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "egg_basilisk"),
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "egg_fish"),
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "egg_spider")
    );
    private static final List<ResourceLocation> EXCLUDED_PROCESSED_EGGS_AND_SEEDS = List.of(
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "egg_alligator_fertilized"),
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "egg_alligator_unfertilized"),
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "alligator_hatch_egg"),
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "seeds_fertilized"),
            ResourceLocation.fromNamespaceAndPath("jurassicsaga", "seeds_unfertilized")
    );

    private EggCollectorGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void approvedBaseEggTagAndTickerCollectEveryConfiguredEgg(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        EggCollectorBlockEntity collector = placeCollector(helper, pos, Direction.NORTH);
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3[] eggPositions = {
                center.add(2.0D, 0.0D, 0.0D),
                center.add(-2.0D, 0.0D, 0.0D),
                center.add(0.0D, 0.0D, 2.0D),
                center.add(0.0D, 0.0D, -2.0D),
                center.add(2.0D, 0.0D, 2.0D),
                center.add(-2.0D, 0.0D, 2.0D),
                center.add(2.0D, 0.0D, -2.0D)
        };
        Item[] approvedItems = new Item[APPROVED_BASE_EGGS.size()];
        ItemEntity[] droppedEggs = new ItemEntity[APPROVED_BASE_EGGS.size()];
        for (int index = 0; index < APPROVED_BASE_EGGS.size(); index++) {
            ResourceLocation id = APPROVED_BASE_EGGS.get(index);
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            if (item == Items.AIR
                    || !new ItemStack(item).is(JSMoreItemTags.EGG_COLLECTOR_COLLECTIBLE_EGGS)) {
                helper.fail("Collectible egg tag is missing " + id);
                return;
            }
            approvedItems[index] = item;
            droppedEggs[index] = spawnItem(helper, eggPositions[index], item, 1, 100);
        }
        if (new ItemStack(Items.STICK).is(JSMoreItemTags.EGG_COLLECTOR_COLLECTIBLE_EGGS)) {
            helper.fail("Collectible egg tag accepted an ordinary item");
            return;
        }
        helper.runAfterDelay(EggCollectorService.SCAN_INTERVAL_TICKS + 2L, () -> {
            for (int index = 0; index < approvedItems.length; index++) {
                if (!droppedEggs[index].isRemoved() || countItem(collector, approvedItems[index]) != 1) {
                    helper.fail("Collector ticker did not collect configured base egg "
                            + APPROVED_BASE_EGGS.get(index));
                    return;
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void tickerWaitsForTheNextScanAfterAnEggReachesOneHundredTicks(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 6));
        EggCollectorBlockEntity collector = placeCollector(helper, pos, Direction.NORTH);
        int spawnDelay = ticksUntilNextScan(helper, pos) + 1;
        ItemEntity[] egg = new ItemEntity[1];

        helper.runAfterDelay(spawnDelay, () -> egg[0] = spawnItem(
                helper,
                Vec3.atCenterOf(pos).add(2.0D, 0.0D, 0.0D),
                Items.EGG,
                1,
                99
        ));
        helper.runAfterDelay(spawnDelay + 2L, () -> {
            if (egg[0] == null
                    || egg[0].getAge() < EggCollectorService.MINIMUM_ITEM_AGE_TICKS
                    || egg[0].isRemoved()
                    || countItem(collector, Items.EGG) != 0) {
                helper.fail("A newly mature egg was collected before the collector's next staggered scan");
            }
        });
        helper.runAfterDelay(spawnDelay + EggCollectorService.SCAN_INTERVAL_TICKS + 1L, () -> {
            if (egg[0] == null || !egg[0].isRemoved() || countItem(collector, Items.EGG) != 1) {
                helper.fail("The block-entity ticker did not collect the mature egg on its next scan");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void tickerIsPositionStaggeredAndCollectsThroughWalls(GameTestHelper helper) {
        BlockPos firstPos = helper.absolutePos(new BlockPos(4, 2, 6));
        BlockPos secondPos = firstPos.south();
        if (Math.floorMod(firstPos.asLong(), EggCollectorService.SCAN_INTERVAL_TICKS)
                == Math.floorMod(secondPos.asLong(), EggCollectorService.SCAN_INTERVAL_TICKS)) {
            helper.fail("Test positions unexpectedly share one scan phase");
            return;
        }
        EggCollectorBlockEntity first = placeCollector(helper, firstPos, Direction.NORTH);
        helper.getLevel().setBlock(firstPos.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        spawnItem(helper, Vec3.atCenterOf(firstPos).add(2.0D, 0.0D, 0.0D), Items.EGG, 1, 100);

        int[] stage = {0};
        long[] firstCollectionTime = {-1L};
        EggCollectorBlockEntity[] second = new EggCollectorBlockEntity[1];
        helper.onEachTick(() -> {
            if (stage[0] == 0 && countItem(first, Items.EGG) == 1) {
                firstCollectionTime[0] = helper.getLevel().getGameTime();
                first.clearContent();
                helper.getLevel().setBlock(firstPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                second[0] = placeCollector(helper, secondPos, Direction.SOUTH);
                spawnItem(helper, Vec3.atCenterOf(secondPos).add(2.0D, 0.0D, 0.0D), Items.EGG, 1, 100);
                stage[0] = 1;
            } else if (stage[0] == 1 && countItem(second[0], Items.EGG) == 1) {
                long secondCollectionTime = helper.getLevel().getGameTime();
                if (Math.floorMod(firstCollectionTime[0], EggCollectorService.SCAN_INTERVAL_TICKS)
                        == Math.floorMod(secondCollectionTime, EggCollectorService.SCAN_INTERVAL_TICKS)) {
                    helper.fail("Collectors at different position phases scanned on the same tick phase");
                    return;
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void playerDroppedBaseEggIsCollectedByTheTicker(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 7));
        EggCollectorBlockEntity collector = placeCollector(helper, pos, Direction.NORTH);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 center = Vec3.atCenterOf(pos).add(2.0D, 0.0D, 0.0D);
        player.setPos(center.x, center.y, center.z);
        ItemEntity dropped = player.drop(new ItemStack(Items.EGG), false);
        if (dropped == null) {
            helper.fail("Mock player could not create a real dropped ItemEntity");
            return;
        }
        setItemAge(dropped, 100);
        dropped.setNoGravity(true);
        dropped.setDeltaMovement(Vec3.ZERO);
        player.setPos(center.x, center.y + 17.0D, center.z);

        helper.runAfterDelay(EggCollectorService.SCAN_INTERVAL_TICKS + 2L, () -> {
            if (!dropped.isRemoved() || countItem(collector, Items.EGG) != 1) {
                helper.fail("The ticker did not collect an eligible egg dropped by a player"
                        + " (added=" + dropped.isAddedToLevel()
                        + ", removed=" + dropped.isRemoved()
                        + ", age=" + dropped.getAge()
                        + ", stored=" + countItem(collector, Items.EGG) + ")");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void processedEggsAndSeedsRemainOutsideWhileBaseEggPassesThroughWall(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 7));
        EggCollectorBlockEntity collector = placeCollector(helper, pos, Direction.NORTH);
        helper.getLevel().setBlock(pos.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ItemEntity baseEgg = spawnItem(
                helper,
                Vec3.atCenterOf(pos).add(2.0D, 0.0D, 0.0D),
                Items.EGG,
                1,
                100
        );
        ItemEntity[] excluded = new ItemEntity[EXCLUDED_PROCESSED_EGGS_AND_SEEDS.size()];
        for (int index = 0; index < EXCLUDED_PROCESSED_EGGS_AND_SEEDS.size(); index++) {
            ResourceLocation id = EXCLUDED_PROCESSED_EGGS_AND_SEEDS.get(index);
            Item item = requiredItem(helper, id);
            if (item == null) {
                return;
            }
            if (new ItemStack(item).is(JSMoreItemTags.EGG_COLLECTOR_COLLECTIBLE_EGGS)) {
                helper.fail("Processed egg or seed is incorrectly collectible: " + id);
                return;
            }
            excluded[index] = spawnItem(
                    helper,
                    Vec3.atCenterOf(pos).add(0.0D, 1.0D + index * 0.2D, 2.0D),
                    item,
                    1,
                    100
            );
        }

        helper.runAfterDelay(EggCollectorService.SCAN_INTERVAL_TICKS + 2L, () -> {
            if (!baseEgg.isRemoved() || countItem(collector, Items.EGG) != 1) {
                helper.fail("A mature base egg was not collected through an opaque wall");
                return;
            }
            for (int index = 0; index < excluded.length; index++) {
                if (excluded[index].isRemoved()
                        || countItem(collector, excluded[index].getItem().getItem()) != 0) {
                    helper.fail("Processed egg or seed was collected: " + EXCLUDED_PROCESSED_EGGS_AND_SEEDS.get(index));
                    return;
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 160)
    public static void vanillaItemMergeKeepsTheYoungerAgeAndDelaysCollection(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 7));
        Vec3 eggPosition = Vec3.atCenterOf(pos).add(2.0D, 0.0D, 0.0D);
        spawnItem(helper, eggPosition, Items.EGG, 1, 0);
        spawnItem(helper, eggPosition, Items.EGG, 1, 80);
        EggCollectorBlockEntity[] collector = new EggCollectorBlockEntity[1];
        ItemEntity[] merged = new ItemEntity[1];

        helper.runAfterDelay(45L, () -> {
            List<ItemEntity> eggs = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(eggPosition, eggPosition).inflate(1.0D),
                    entity -> entity.getItem().is(Items.EGG)
            );
            if (eggs.size() != 1 || eggs.getFirst().getItem().getCount() != 2 || eggs.getFirst().getAge() >= 100) {
                helper.fail("Vanilla did not merge the egg stack while retaining the younger age");
                return;
            }
            merged[0] = eggs.getFirst();
            collector[0] = placeCollector(helper, pos, Direction.NORTH);
        });
        helper.runAfterDelay(60L, () -> {
            if (merged[0] == null
                    || merged[0].isRemoved()
                    || collector[0] == null
                    || countItem(collector[0], Items.EGG) != 0) {
                helper.fail("Merged eggs were collected according to the older entity age");
            }
        });
        helper.runAfterDelay(120L, () -> {
            if (merged[0] == null || !merged[0].isRemoved() || countItem(collector[0], Items.EGG) != 2) {
                helper.fail("Merged eggs were not collected after the retained younger age matured");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void rangeIsSphericalAndIncludesExactRadiusBoundary(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 8));
        EggCollectorBlockEntity collector = placeCollector(helper, pos, Direction.NORTH);
        Vec3 center = Vec3.atCenterOf(pos);
        ItemEntity boundary = spawnItem(helper, center.add(16.0D, 0.0D, 0.0D), Items.EGG, 1, 100);
        ItemEntity outside = spawnItem(helper, center.add(16.01D, 0.0D, 0.0D), Items.EGG, 1, 100);

        int transferred = EggCollectorService.collect(helper.getLevel(), pos, collector);
        if (transferred != 1 || !boundary.isRemoved() || outside.isRemoved()) {
            helper.fail("Collector did not apply the closed radius-16 sphere");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void partialAndFullInventoryNeverDestroyRemainders(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 8));
        EggCollectorBlockEntity collector = placeCollector(helper, pos, Direction.NORTH);
        for (int slot = 0; slot < collector.getContainerSize() - 1; slot++) {
            collector.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        int eggStackLimit = new ItemStack(Items.EGG).getMaxStackSize();
        collector.setItem(collector.getContainerSize() - 1, new ItemStack(Items.EGG, eggStackLimit - 1));
        ItemEntity partial = spawnItem(helper, Vec3.atCenterOf(pos), Items.EGG, 3, 100);

        int firstTransfer = EggCollectorService.transferToInventory(partial, collector.getItemHandler());
        if (firstTransfer != 1 || partial.isRemoved() || partial.getItem().getCount() != 2) {
            helper.fail("Partial insertion did not preserve the dropped remainder");
            return;
        }
        collector.setItem(collector.getContainerSize() - 1, new ItemStack(Items.EGG, eggStackLimit));
        int secondTransfer = EggCollectorService.transferToInventory(partial, collector.getItemHandler());
        if (secondTransfer != 0 || partial.isRemoved() || partial.getItem().getCount() != 2) {
            helper.fail("Full collector consumed or changed an egg entity");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void overlappingCollectorsCannotDuplicateEggs(GameTestHelper helper) {
        BlockPos firstPos = helper.absolutePos(new BlockPos(6, 2, 7));
        BlockPos secondPos = helper.absolutePos(new BlockPos(8, 2, 7));
        EggCollectorBlockEntity first = placeCollector(helper, firstPos, Direction.NORTH);
        EggCollectorBlockEntity second = placeCollector(helper, secondPos, Direction.SOUTH);
        ItemEntity eggs = spawnItem(helper, Vec3.atCenterOf(firstPos).add(1.0D, 0.0D, 0.0D), Items.EGG, 5, 100);

        EggCollectorService.collect(helper.getLevel(), firstPos, first);
        EggCollectorService.collect(helper.getLevel(), secondPos, second);
        if (!eggs.isRemoved() || countItem(first, Items.EGG) + countItem(second, Items.EGG) != 5) {
            helper.fail("Overlapping collectors duplicated or lost eggs");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void inventoryPersistsEverySideAndRealHoppersTransferArbitraryItems(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(6, 2, 8));
        EggCollectorBlock block = JSMoreBlocks.EGG_COLLECTOR.get();
        EggCollectorBlockEntity collector = placeCollector(helper, pos, Direction.WEST);
        collector.setItem(0, new ItemStack(Items.DIAMOND, 3));
        IItemHandler expected = null;
        for (Direction direction : Direction.values()) {
            IItemHandler actual = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (actual == null || (expected != null && actual != expected)) {
                helper.fail("Collector did not expose one stable handler on " + direction);
                return;
            }
            ItemStack insertionRemainder = actual.insertItem(2, new ItemStack(Items.CARROT), false);
            ItemStack extracted = actual.extractItem(2, 1, false);
            if (!insertionRemainder.isEmpty() || !extracted.is(Items.CARROT) || extracted.getCount() != 1) {
                helper.fail("Collector capability did not support insertion and extraction on " + direction);
                return;
            }
            expected = actual;
        }
        IItemHandler nullSide = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (expected == null
                || expected != nullSide
                || expected.getSlots() != EggCollectorBlockEntity.CONTAINER_SIZE
                || expected.getSlots() != 18) {
            helper.fail("Collector capability shape is not a stable 18-slot inventory");
            return;
        }
        ItemStack arbitraryRemainder = expected.insertItem(1, new ItemStack(Items.CARROT, 2), false);
        CompoundTag saved = collector.saveWithoutMetadata(helper.getLevel().registryAccess());
        ListTag savedItems = saved.getList("Items", Tag.TAG_COMPOUND);
        for (int index = 0; index < savedItems.size(); index++) {
            if ((savedItems.getCompound(index).getByte("Slot") & 255) >= EggCollectorBlockEntity.CONTAINER_SIZE) {
                helper.fail("Collector saved an item beyond its 18-slot inventory");
                return;
            }
        }
        EggCollectorBlockEntity loaded = new EggCollectorBlockEntity(pos, collector.getBlockState());
        loaded.loadCustomOnly(saved, helper.getLevel().registryAccess());
        int signal = block.getAnalogOutputSignal(collector.getBlockState(), helper.getLevel(), pos);
        if (!arbitraryRemainder.isEmpty()
                || loaded.getContainerSize() != 18
                || loaded.getItem(0).getCount() != 3
                || !loaded.getItem(1).is(Items.CARROT)
                || loaded.getItem(1).getCount() != 2
                || signal <= 0) {
            helper.fail("Inventory capability, persistence, or comparator behavior regressed");
            return;
        }

        collector.clearContent();
        for (int slot = 0; slot < EggCollectorBlockEntity.CONTAINER_SIZE / 2; slot++) {
            collector.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        if (block.getAnalogOutputSignal(collector.getBlockState(), helper.getLevel(), pos) != 8) {
            helper.fail("Comparator fullness did not use the collector's 18-slot capacity");
            return;
        }
        collector.clearContent();
        BlockPos inputHopperPos = pos.west();
        BlockState sideInputHopper = Blocks.HOPPER.defaultBlockState()
                .setValue(HopperBlock.FACING, Direction.EAST)
                .setValue(HopperBlock.ENABLED, true);
        helper.getLevel().setBlock(inputHopperPos, sideInputHopper, Block.UPDATE_ALL);
        if (!(helper.getLevel().getBlockEntity(inputHopperPos) instanceof HopperBlockEntity inputHopper)) {
            helper.fail("Could not create the real side-input hopper fixture");
            return;
        }
        inputHopper.setItem(0, new ItemStack(Items.CARROT));

        helper.runAfterDelay(12L, () -> {
            if (!inputHopper.isEmpty() || countItem(collector, Items.CARROT) != 1) {
                helper.fail("A real side-facing hopper did not insert an arbitrary item into the collector");
                return;
            }
            helper.getLevel().setBlock(inputHopperPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos outputHopperPos = pos.below();
            BlockState downwardOutputHopper = Blocks.HOPPER.defaultBlockState()
                    .setValue(HopperBlock.FACING, Direction.DOWN)
                    .setValue(HopperBlock.ENABLED, true);
            helper.getLevel().setBlock(outputHopperPos, downwardOutputHopper, Block.UPDATE_ALL);
            if (!(helper.getLevel().getBlockEntity(outputHopperPos) instanceof HopperBlockEntity outputHopper)) {
                helper.fail("Could not create the real below-collector output hopper fixture");
                return;
            }
            helper.runAfterDelay(24L, () -> {
                if (countItem(collector, Items.CARROT) != 0 || countItem(outputHopper, Items.CARROT) != 1) {
                    helper.fail("A real hopper below the collector did not extract an arbitrary item");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void legacyThirdRowSlotsAreIgnoredAndNeverResaved(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(6, 2, 8));
        EggCollectorBlockEntity collector = placeCollector(helper, pos, Direction.NORTH);
        ListTag legacyItems = new ListTag();
        CompoundTag retained = new CompoundTag();
        retained.putByte("Slot", (byte) 17);
        legacyItems.add(new ItemStack(Items.DIAMOND, 2).save(helper.getLevel().registryAccess(), retained));
        CompoundTag discarded = new CompoundTag();
        discarded.putByte("Slot", (byte) 18);
        legacyItems.add(new ItemStack(Items.CARROT, 3).save(helper.getLevel().registryAccess(), discarded));
        CompoundTag legacyData = new CompoundTag();
        legacyData.put("Items", legacyItems);

        collector.loadCustomOnly(legacyData, helper.getLevel().registryAccess());
        if (collector.getContainerSize() != 18
                || !collector.getItem(17).is(Items.DIAMOND)
                || collector.getItem(17).getCount() != 2
                || countItem(collector, Items.CARROT) != 0) {
            helper.fail("Collector did not discard legacy slots outside its two-row inventory");
            return;
        }

        ListTag resavedItems = collector.saveWithoutMetadata(helper.getLevel().registryAccess())
                .getList("Items", Tag.TAG_COMPOUND);
        for (int index = 0; index < resavedItems.size(); index++) {
            if ((resavedItems.getCompound(index).getByte("Slot") & 255) >= 18) {
                helper.fail("Collector resaved a legacy slot outside its two-row inventory");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void menuUsesTwoChestRowsAndEnforcesDistanceAndBlockPresence(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(6, 2, 8));
        EggCollectorBlockEntity collector = placeCollector(helper, pos, Direction.NORTH);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 center = Vec3.atCenterOf(pos);
        player.setPos(center.x, center.y, center.z);
        AbstractContainerMenu menu = collector.createMenu(7, player.getInventory(), player);
        if (!(menu instanceof ChestMenu chest)
                || chest.getType() != MenuType.GENERIC_9x2
                || chest.getRowCount() != 2
                || chest.getContainer() != collector
                || chest.slots.size() != 54
                || !chest.stillValid(player)) {
            helper.fail("Collector did not create a valid vanilla two-row chest menu");
            return;
        }

        player.setPos(center.x + 9.0D, center.y, center.z);
        if (chest.stillValid(player)) {
            helper.fail("Collector menu remained valid beyond the vanilla eight-block distance");
            return;
        }
        player.setPos(center.x, center.y, center.z);
        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (chest.stillValid(player)) {
            helper.fail("Collector menu remained valid after its block entity was removed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void breakingCollectorDropsStoredContents(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(6, 2, 8));
        EggCollectorBlockEntity collector = placeCollector(helper, pos, Direction.EAST);
        collector.setItem(0, new ItemStack(Items.DIAMOND, 2));

        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        int droppedDiamonds = helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(pos).inflate(2.0D),
                        entity -> entity.getItem().is(Items.DIAMOND)
                ).stream()
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
        if (droppedDiamonds != 2 || helper.getLevel().getBlockEntity(pos) != null) {
            helper.fail("Breaking the collector did not drop its complete stored inventory");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 100)
    public static void breakingNamedCollectorCopiesOnlyNameAndDropsInventorySeparately(GameTestHelper helper) {
        BlockPos supportPos = helper.absolutePos(new BlockPos(6, 1, 8));
        BlockPos collectorPos = supportPos.above();
        helper.getLevel().setBlock(supportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 supportCenter = Vec3.atCenterOf(supportPos);
        player.setPos(supportCenter.x, supportCenter.y + 1.0D, supportCenter.z + 2.0D);
        Component customName = Component.literal("Field Nest");
        ItemStack placement = new ItemStack(JSMoreItems.EGG_COLLECTOR.get());
        placement.set(DataComponents.CUSTOM_NAME, customName);
        player.setItemInHand(InteractionHand.MAIN_HAND, placement);
        BlockHitResult hit = new BlockHitResult(
                supportCenter.add(0.0D, 0.5D, 0.0D),
                Direction.UP,
                supportPos,
                false
        );
        InteractionResult placementResult = placement.useOn(new BlockPlaceContext(
                player,
                InteractionHand.MAIN_HAND,
                placement,
                hit
        ));
        if (!placementResult.consumesAction()
                || !(helper.getLevel().getBlockEntity(collectorPos) instanceof EggCollectorBlockEntity collector)
                || collector.getCustomName() == null
                || !customName.getString().equals(collector.getCustomName().getString())) {
            helper.fail("A custom-named collector could not be placed as a real block item");
            return;
        }
        collector.setItem(0, new ItemStack(Items.DIAMOND, 2));
        if (!helper.getLevel().destroyBlock(collectorPos, true, player)) {
            helper.fail("Could not destroy the custom-named collector through the real loot path");
            return;
        }

        helper.runAfterDelay(1L, () -> {
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(collectorPos).inflate(3.0D)
            );
            List<ItemStack> collectorDrops = drops.stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(JSMoreItems.EGG_COLLECTOR.get()))
                    .toList();
            int diamonds = drops.stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount)
                    .sum();
            if (collectorDrops.size() != 1
                    || collectorDrops.getFirst().getCount() != 1
                    || collectorDrops.getFirst().get(DataComponents.CUSTOM_NAME) == null
                    || !customName.getString().equals(
                            collectorDrops.getFirst().get(DataComponents.CUSTOM_NAME).getString()
                    )
                    || collectorDrops.getFirst().has(DataComponents.CONTAINER)
                    || diamonds != 2) {
                helper.fail("Collector loot did not preserve only the name while dropping inventory separately");
                return;
            }
            helper.succeed();
        });
    }

    private static EggCollectorBlockEntity placeCollector(
            GameTestHelper helper,
            BlockPos pos,
            Direction facing
    ) {
        BlockState state = JSMoreBlocks.EGG_COLLECTOR.get()
                .defaultBlockState()
                .setValue(EggCollectorBlock.FACING, facing);
        helper.getLevel().setBlock(pos, state, Block.UPDATE_ALL);
        return (EggCollectorBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static ItemEntity spawnItem(
            GameTestHelper helper,
            Vec3 position,
            Item item,
            int count,
            int age
    ) {
        ItemEntity entity = new ItemEntity(
                helper.getLevel(),
                position.x,
                position.y,
                position.z,
                new ItemStack(item, count)
        );
        setItemAge(entity, age);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void setItemAge(ItemEntity entity, int age) {
        CompoundTag data = new CompoundTag();
        entity.addAdditionalSaveData(data);
        data.putShort("Age", (short) age);
        entity.readAdditionalSaveData(data);
    }

    private static int ticksUntilNextScan(GameTestHelper helper, BlockPos pos) {
        long gameTime = helper.getLevel().getGameTime();
        for (int delay = 1; delay <= EggCollectorService.SCAN_INTERVAL_TICKS; delay++) {
            if (EggCollectorService.shouldScan(gameTime + delay, pos)) {
                return delay;
            }
        }
        throw new AssertionError("Collector has no scan tick in one interval");
    }

    private static Item requiredItem(GameTestHelper helper, ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (item == Items.AIR) {
            helper.fail("Required Jurassic Saga registry item is missing: " + id);
            return null;
        }
        return item;
    }

    private static int countItem(Container collector, Item item) {
        int count = 0;
        for (int slot = 0; slot < collector.getContainerSize(); slot++) {
            ItemStack stack = collector.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
