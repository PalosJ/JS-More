package com.palos.jsmore.mixin;

import com.palos.jsmore.server.system.capture.CapturedGrowthAccess;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.entity.animal.entity.obj.modules.JSAnimalModuleBase;
import jp.jurassicsaga.server.entity.animal.entity.obj.modules.obj.JSGrowthStageModule;
import jp.jurassicsaga.server.entity.animal.entity.obj.info.AnimalGrowthStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = JSGrowthStageModule.class, remap = false)
public abstract class JSGrowthStageCaptureMixin extends JSAnimalModuleBase implements CapturedGrowthAccess {
    @Shadow private long lastSeenGameTime;
    @Shadow private boolean offlineGrowthPending;

    protected JSGrowthStageCaptureMixin(JSAnimalBase owner) { super(owner); }

    @Invoker("applyOfflineGrowth") protected abstract void jsmore$applyOfflineGrowth();

    @Override
    public void jsmore$settlePendingGrowth() {
        if (this.offlineGrowthPending) jsmore$settleGrowthAt(this.owner.level().getGameTime());
    }

    @Override
    public double jsmore$settleGrowthAt(long targetTime, boolean applyHealing) {
        if (this.owner.level().isClientSide || this.owner.isDead()) return 0.0D;
        JSGrowthStageModule growth = (JSGrowthStageModule) (Object) this;
        float health = this.owner.getHealth();
        float progress = growth.getGrowthProgress();
        double rate = (double) growth.getGrowthMultiplier() * growth.getGrowthMultiplier();
        boolean wasBaby = growth.getGrowthStage() != AnimalGrowthStage.ADULT;
        long now = Math.max(0L, this.owner.level().getGameTime());
        long target = Math.max(0L, Math.min(now, targetTime));
        if (this.lastSeenGameTime >= 0L && this.lastSeenGameTime < target) {
            long elapsed = target - this.lastSeenGameTime;
            // The upstream private method reads world time. Rebase its anchor, never the world's clock.
            this.lastSeenGameTime = now - elapsed;
            this.jsmore$applyOfflineGrowth();
        }
        this.lastSeenGameTime = target;
        this.offlineGrowthPending = false;
        double healing = wasBaby && Double.isFinite(rate) && rate > 0.0D
                ? Math.max(0.0D, growth.getGrowthProgress() - progress) / rate * 0.01D : 0.0D;
        if (!applyHealing) this.owner.setHealth(health);
        if (growth.getGrowthProgress() >= growth.getMaxGrowth()
                && growth.getGrowthStage() != AnimalGrowthStage.ADULT) {
            growth.setGrowthStage(AnimalGrowthStage.ADULT);
        }
        // Rebuild the upstream cached hitbox before any release-position check. Its collision
        // query checks loaded chunks; a temporary animal must not retain a collision push.
        var velocity = this.owner.getDeltaMovement();
        growth.tickGrowthCollisionPush();
        this.owner.setDeltaMovement(velocity);
        return healing;
    }
}
