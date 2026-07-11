package com.palos.jsrevise.server.entity.projectile;

import com.palos.jsrevise.server.registry.JSReviseEntityTypes;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class AnestheticDartEntity extends AbstractArrow {
    private static final float HIT_DAMAGE = 1.0F;

    public AnestheticDartEntity(EntityType<? extends AnestheticDartEntity> entityType, Level level) {
        super(entityType, level);
    }

    public AnestheticDartEntity(Level level, LivingEntity owner, ItemStack dartStack) {
        super(
                JSReviseEntityTypes.ANESTHETIC_DART.get(),
                owner,
                level,
                canonicalDartStack(dartStack),
                null
        );
        if (owner instanceof Player player && player.isCreative()) {
            this.pickup = Pickup.CREATIVE_ONLY;
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide
                && result.getEntity() instanceof JSAnimalBase animal
                && DinosaurAnestheticSystem.tryApplyAnestheticInjection(animal)) {
            Entity owner = getOwner();
            animal.hurt(damageSources().arrow(this, owner == null ? this : owner), HIT_DAMAGE);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(JSReviseItems.ANESTHETIC_DART.get());
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05D;
    }

    private static ItemStack canonicalDartStack(ItemStack stack) {
        return stack.is(JSReviseItems.ANESTHETIC_DART.get())
                ? stack.copyWithCount(1)
                : new ItemStack(JSReviseItems.ANESTHETIC_DART.get());
    }
}
