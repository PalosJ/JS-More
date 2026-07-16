package com.palos.jsmore.server.item;

import com.palos.jsmore.server.entity.projectile.AnestheticDartEntity;
import com.palos.jsmore.server.registry.JSMoreItems;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class AnestheticCrossbowItem extends CrossbowItem {
    private static final int FIRE_COOLDOWN_TICKS = 10;
    private static final int MAX_LOADED_DARTS = 6;
    static final String LOADED_DARTS_TAG = "LoadedDarts";
    private static final Predicate<ItemStack> SUPPORTED_PROJECTILES =
            stack -> stack.is(JSMoreItems.ANESTHETIC_DART.get());

    public AnestheticCrossbowItem() {
        super(new Properties().stacksTo(1).durability(650));
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return SUPPORTED_PROJECTILES;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return SUPPORTED_PROJECTILES;
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles(ItemStack stack) {
        return SUPPORTED_PROJECTILES;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles(ItemStack stack) {
        return SUPPORTED_PROJECTILES;
    }

    @Override
    public ItemStack getDefaultCreativeAmmo(Player player, ItemStack projectileWeaponItem) {
        return new ItemStack(JSMoreItems.ANESTHETIC_DART.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack weapon = player.getItemInHand(hand);
        normalizeLoadedDarts(weapon);
        if (!this.canStartLoading(player, weapon)) {
            return InteractionResultHolder.fail(weapon);
        }

        this.beginReload(weapon);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(weapon);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        int currentLoadedDarts = normalizeLoadedDarts(stack);
        int usedTicks = this.getUseDuration(stack, entityLiving) - timeLeft;
        if (usedTicks < getChargeDuration(stack, entityLiving)) {
            if (currentLoadedDarts > 0) {
                this.armNextDart(stack);
            }
            return;
        }

        int addedDarts = this.loadMagazine(entityLiving, stack);
        if (addedDarts <= 0) {
            return;
        }

        level.playSound(
                null,
                entityLiving.getX(),
                entityLiving.getY(),
                entityLiving.getZ(),
                SoundEvents.CROSSBOW_LOADING_END,
                SoundSource.PLAYERS,
                1.0F,
                0.95F
        );
    }

    public boolean tryFireLoadedDart(Level level, Player player, InteractionHand hand) {
        ItemStack weapon = player.getItemInHand(hand);
        if (weapon.getItem() != this) {
            return false;
        }
        int loadedDarts = normalizeLoadedDarts(weapon);
        if (loadedDarts <= 0 || !hasArmedDart(weapon) || this.isOnFireCooldown(player)) {
            return false;
        }

        this.performShooting(level, player, hand, weapon, getShootingPower(), 0.30F, null);
        player.getCooldowns().addCooldown(this, FIRE_COOLDOWN_TICKS);
        return true;
    }

    /**
     * @deprecated Use {@link #tryFireLoadedDart(Level, Player, InteractionHand)}.
     */
    @Deprecated(forRemoval = false)
    public boolean tryFireLoadedSyringe(Level level, Player player, InteractionHand hand) {
        return tryFireLoadedDart(level, player, hand);
    }

    public boolean hasLoadedDarts(ItemStack weapon) {
        return getLoadedDartCount(weapon) > 0;
    }

    public int getLoadedDartCount(ItemStack weapon) {
        CustomData customData = weapon.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return 0;
        }
        CompoundTag tag = customData.copyTag();
        if (tag.contains(LOADED_DARTS_TAG)) {
            return sanitizeLoadedCount(tag.getInt(LOADED_DARTS_TAG));
        }
        return 0;
    }

    /**
     * @deprecated This method now reports loaded darts. Use {@link #hasLoadedDarts(ItemStack)}.
     */
    @Deprecated(forRemoval = false)
    public boolean hasLoadedSyringes(ItemStack weapon) {
        return hasLoadedDarts(weapon);
    }

    /**
     * @deprecated This method now reports loaded darts. Use {@link #getLoadedDartCount(ItemStack)}.
     */
    @Deprecated(forRemoval = false)
    public int getLoadedSyringeCount(ItemStack weapon) {
        return getLoadedDartCount(weapon);
    }

    public boolean isOnFireCooldown(Player player) {
        return player.getCooldowns().isOnCooldown(this);
    }

    @Override
    public void performShooting(
            Level level,
            LivingEntity shooter,
            InteractionHand hand,
            ItemStack weapon,
            float velocity,
            float inaccuracy,
            LivingEntity target
    ) {
        int loadedDarts = normalizeLoadedDarts(weapon);
        if (loadedDarts <= 0 || !hasArmedDart(weapon)) {
            clearLoadedDarts(weapon);
            return;
        }

        super.performShooting(level, shooter, hand, weapon, velocity, inaccuracy, target);
        setLoadedDarts(weapon, loadedDarts - 1);
        if (!weapon.isEmpty() && loadedDarts > 1) {
            armNextDart(weapon);
        }
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
        ItemStack projectileStack = ammo.is(JSMoreItems.ANESTHETIC_DART.get())
                ? ammo.copyWithCount(1)
                : new ItemStack(JSMoreItems.ANESTHETIC_DART.get());
        return new AnestheticDartEntity(level, shooter, projectileStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(
                Component.translatable(
                        "item.jsmore.anesthetic_crossbow.loaded",
                        getLoadedDartCount(stack),
                        MAX_LOADED_DARTS
                ).withStyle(ChatFormatting.BLUE)
        );
    }

    int normalizeLoadedDarts(ItemStack weapon) {
        int loadedDarts = getLoadedDartCount(weapon);
        if (loadedDarts <= 0) {
            clearLoadedDarts(weapon);
            return 0;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> tag.putInt(LOADED_DARTS_TAG, loadedDarts));
        armNextDart(weapon);
        return loadedDarts;
    }

    private int loadMagazine(LivingEntity shooter, ItemStack weapon) {
        int currentLoadedDarts = getLoadedDartCount(weapon);
        int remainingCapacity = MAX_LOADED_DARTS - currentLoadedDarts;
        if (remainingCapacity <= 0) {
            return 0;
        }

        int loadedDarts = shooter instanceof Player player
                ? consumePlayerAmmo(player, remainingCapacity)
                : 1;
        if (loadedDarts <= 0) {
            return 0;
        }

        setLoadedDarts(weapon, currentLoadedDarts + loadedDarts);
        armNextDart(weapon);
        return loadedDarts;
    }

    private boolean canStartLoading(Player player, ItemStack weapon) {
        int loadedDarts = getLoadedDartCount(weapon);
        if (loadedDarts >= MAX_LOADED_DARTS) {
            return false;
        }
        return player.getAbilities().instabuild || hasAmmoToLoad(player);
    }

    private boolean hasAmmoToLoad(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(JSMoreItems.ANESTHETIC_DART.get())) {
                return true;
            }
        }
        return false;
    }

    private int consumePlayerAmmo(Player player, int maxToLoad) {
        if (player.getAbilities().instabuild) {
            return maxToLoad;
        }

        int remaining = maxToLoad;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack inventoryStack = player.getInventory().getItem(slot);
            if (!inventoryStack.is(JSMoreItems.ANESTHETIC_DART.get())) {
                continue;
            }

            int toRemove = Math.min(remaining, inventoryStack.getCount());
            inventoryStack.shrink(toRemove);
            remaining -= toRemove;
        }
        return maxToLoad - remaining;
    }

    private void armNextDart(ItemStack weapon) {
        weapon.set(
                DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.of(new ItemStack(JSMoreItems.ANESTHETIC_DART.get()))
        );
    }

    private void beginReload(ItemStack weapon) {
        if (getLoadedDartCount(weapon) > 0) {
            weapon.remove(DataComponents.CHARGED_PROJECTILES);
        }
    }

    private boolean hasArmedDart(ItemStack weapon) {
        ChargedProjectiles projectiles = weapon.get(DataComponents.CHARGED_PROJECTILES);
        if (projectiles == null || projectiles.getItems().size() != 1) {
            return false;
        }
        ItemStack projectile = projectiles.getItems().getFirst();
        return projectile.getCount() == 1 && projectile.is(JSMoreItems.ANESTHETIC_DART.get());
    }

    private void setLoadedDarts(ItemStack weapon, int loadedDarts) {
        int safeLoadedDarts = sanitizeLoadedCount(loadedDarts);
        if (safeLoadedDarts <= 0) {
            clearLoadedDarts(weapon);
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> tag.putInt(LOADED_DARTS_TAG, safeLoadedDarts));
    }

    private void clearLoadedDarts(ItemStack weapon) {
        CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> tag.remove(LOADED_DARTS_TAG));
        CustomData customData = weapon.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.isEmpty()) {
            weapon.remove(DataComponents.CUSTOM_DATA);
        }
        weapon.remove(DataComponents.CHARGED_PROJECTILES);
    }

    private static int sanitizeLoadedCount(int count) {
        return Mth.clamp(count, 0, MAX_LOADED_DARTS);
    }

    private static float getShootingPower() {
        return 3.6F;
    }
}
