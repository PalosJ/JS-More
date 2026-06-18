package com.palos.jsrevise.server.item;

import com.palos.jsrevise.server.entity.projectile.AnestheticSyringeProjectile;
import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.ArrayList;
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
    private static final int MAX_LOADED_SYRINGES = 6;
    private static final String LOADED_SYRINGES_TAG = "LoadedSyringes";
    private static final Predicate<ItemStack> SUPPORTED_PROJECTILES =
            stack -> stack.is(JSReviseItems.ANESTHETIC_SYRINGE.get());

    public AnestheticCrossbowItem() {
        super(new Properties().stacksTo(1).durability(465));
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
        return new ItemStack(JSReviseItems.ANESTHETIC_SYRINGE.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack weapon = player.getItemInHand(hand);
        if (!this.canStartLoading(player, weapon)) {
            return InteractionResultHolder.fail(weapon);
        }

        this.beginReload(weapon);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(weapon);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        int currentLoadedSyringes = this.getLoadedSyringes(stack);
        int usedTicks = this.getUseDuration(stack, entityLiving) - timeLeft;
        if (usedTicks < getChargeDuration(stack, entityLiving)) {
            if (currentLoadedSyringes > 0) {
                this.armNextSyringe(stack);
            }
            return;
        }

        int addedSyringes = this.loadMagazine(entityLiving, stack);
        if (addedSyringes <= 0) {
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

    public boolean tryFireLoadedSyringe(Level level, Player player, InteractionHand hand) {
        ItemStack weapon = player.getItemInHand(hand);
        if (weapon.getItem() != this || this.getLoadedSyringes(weapon) <= 0 || !isCharged(weapon) || this.isOnFireCooldown(player)) {
            return false;
        }

        this.performShooting(level, player, hand, weapon, getShootingPower(), 0.30F, null);
        player.getCooldowns().addCooldown(this, FIRE_COOLDOWN_TICKS);
        return true;
    }

    public boolean hasLoadedSyringes(ItemStack weapon) {
        return this.getLoadedSyringes(weapon) > 0;
    }

    public int getLoadedSyringeCount(ItemStack weapon) {
        return this.getLoadedSyringes(weapon);
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
        int loadedSyringes = this.getLoadedSyringes(weapon);
        if (loadedSyringes <= 0) {
            super.performShooting(level, shooter, hand, weapon, velocity, inaccuracy, target);
            this.clearLoadedSyringes(weapon);
            return;
        }

        super.performShooting(level, shooter, hand, weapon, velocity, inaccuracy, target);
        this.setLoadedSyringes(weapon, loadedSyringes - 1);
        if (!weapon.isEmpty() && loadedSyringes > 1) {
            this.armNextSyringe(weapon);
        }
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
        AnestheticSyringeProjectile projectile = new AnestheticSyringeProjectile(level, shooter);
        ItemStack projectileStack = ammo.copy();
        projectileStack.setCount(1);
        projectile.setItem(projectileStack);
        return projectile;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(
                Component.translatable(
                        "item.jsrevise.anesthetic_crossbow.loaded",
                        this.getLoadedSyringes(stack),
                        MAX_LOADED_SYRINGES
                ).withStyle(ChatFormatting.BLUE)
        );
    }

    private int loadMagazine(LivingEntity shooter, ItemStack weapon) {
        int currentLoadedSyringes = this.getLoadedSyringes(weapon);
        int remainingCapacity = MAX_LOADED_SYRINGES - currentLoadedSyringes;
        if (remainingCapacity <= 0) {
            return 0;
        }

        int loadedSyringes = shooter instanceof Player player
                ? this.consumePlayerAmmo(player, remainingCapacity)
                : 1;
        if (loadedSyringes <= 0) {
            return 0;
        }

        this.setLoadedSyringes(weapon, currentLoadedSyringes + loadedSyringes);
        this.armNextSyringe(weapon);
        return loadedSyringes;
    }

    private boolean canStartLoading(Player player, ItemStack weapon) {
        int loadedSyringes = this.getLoadedSyringes(weapon);
        if (loadedSyringes >= MAX_LOADED_SYRINGES) {
            return false;
        }

        return player.getAbilities().instabuild || this.hasAmmoToLoad(player);
    }

    private boolean hasAmmoToLoad(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(JSReviseItems.ANESTHETIC_SYRINGE.get())) {
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
            if (!inventoryStack.is(JSReviseItems.ANESTHETIC_SYRINGE.get())) {
                continue;
            }

            int toRemove = Math.min(remaining, inventoryStack.getCount());
            inventoryStack.shrink(toRemove);
            remaining -= toRemove;
        }

        return maxToLoad - remaining;
    }

    private void armNextSyringe(ItemStack weapon) {
        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(this.createLoadedProjectileList(1)));
    }

    private void beginReload(ItemStack weapon) {
        if (this.getLoadedSyringes(weapon) > 0) {
            weapon.remove(DataComponents.CHARGED_PROJECTILES);
        }
    }

    private List<ItemStack> createLoadedProjectileList(int count) {
        List<ItemStack> projectiles = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            projectiles.add(new ItemStack(JSReviseItems.ANESTHETIC_SYRINGE.get()));
        }

        return projectiles;
    }

    private int getLoadedSyringes(ItemStack weapon) {
        CustomData customData = weapon.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(LOADED_SYRINGES_TAG)) {
            return isCharged(weapon) ? 1 : 0;
        }

        CompoundTag tag = customData.copyTag();
        return Mth.clamp(
                Math.max(tag.getInt(LOADED_SYRINGES_TAG), isCharged(weapon) ? 1 : 0),
                0,
                MAX_LOADED_SYRINGES
        );
    }

    private void setLoadedSyringes(ItemStack weapon, int loadedSyringes) {
        if (loadedSyringes <= 0) {
            this.clearLoadedSyringes(weapon);
            return;
        }

        int safeLoadedSyringes = Mth.clamp(loadedSyringes, 0, MAX_LOADED_SYRINGES);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                weapon,
                tag -> tag.putInt(LOADED_SYRINGES_TAG, safeLoadedSyringes)
        );
    }

    private void clearLoadedSyringes(ItemStack weapon) {
        CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> tag.remove(LOADED_SYRINGES_TAG));
        if (weapon.has(DataComponents.CUSTOM_DATA)) {
            CustomData customData = weapon.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.isEmpty()) {
                weapon.remove(DataComponents.CUSTOM_DATA);
            }
        }
    }

    private static float getShootingPower() {
        return 3.6F;
    }
}
