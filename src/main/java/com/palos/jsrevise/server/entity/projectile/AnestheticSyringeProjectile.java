package com.palos.jsrevise.server.entity.projectile;

import com.palos.jsrevise.server.registry.JSReviseEntityTypes;
import com.palos.jsrevise.server.registry.JSReviseItems;
import com.palos.jsrevise.server.system.anesthetic.DinosaurAnestheticSystem;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class AnestheticSyringeProjectile extends ThrowableItemProjectile {
    private static final float HIT_DAMAGE = 1.0F;

    public AnestheticSyringeProjectile(EntityType<? extends AnestheticSyringeProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public AnestheticSyringeProjectile(Level level, LivingEntity owner) {
        super(JSReviseEntityTypes.ANESTHETIC_SYRINGE_PROJECTILE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return JSReviseItems.ANESTHETIC_SYRINGE.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide && result.getEntity() instanceof JSAnimalBase animal) {
            animal.hurt(damageSources().thrown(this, getOwner() instanceof LivingEntity livingOwner ? livingOwner : null), HIT_DAMAGE);
            DinosaurAnestheticSystem.applyAnestheticInjection(animal);
        }

        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        discard();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0075D;
    }
}
