package com.palos.jsrevise.server.system.capture;

import com.palos.jsrevise.JSRevise;
import com.palos.jsrevise.compat.aeronautics.CaptureBoxWorldContext;
import com.palos.jsrevise.server.registry.JSReviseItems;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Projects capture-box block drops out of a Sable plot without changing ordinary world drops. */
@EventBusSubscriber(modid = JSRevise.MOD_ID)
public final class CaptureBoxDropProjectionHandler {
    private CaptureBoxDropProjectionHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ItemEntity item)
                || !isCaptureBoxDrop(item.getItem())) {
            return;
        }
        Vec3 localPosition = item.position();
        if (!isFinite(localPosition) || !CaptureBoxWorldContext.isPlotGrid(level, localPosition)) {
            return;
        }
        BlockPos localBlock = BlockPos.containing(localPosition);
        CaptureBoxWorldContext context = CaptureBoxWorldContext.resolve(
                level,
                localBlock,
                new AABB(localBlock)
        );
        if (!context.operational() || context.spaceIdentity().sublevelId().isEmpty()) {
            return;
        }
        try {
            Vec3 globalPosition = context.localToGlobal(localPosition);
            Vec3 globalThrowVelocity = context.localNormalToGlobal(item.getDeltaMovement());
            Vec3 craftVelocity = context.pointVelocityBlocksPerTick(localPosition);
            Optional<DropProjection> projection = finishProjection(
                    globalPosition,
                    globalThrowVelocity,
                    craftVelocity,
                    CaptureBoxWorldContext.isPlotGrid(level, globalPosition)
            );
            if (projection.isEmpty()) {
                return;
            }
            DropProjection resolved = projection.orElseThrow();
            item.setPos(resolved.position().x, resolved.position().y, resolved.position().z);
            item.setDeltaMovement(resolved.velocity());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // Exact-profile transform failures leave the original entity untouched; fail-closed movement gating
            // prevents new capture boxes entering an unsupported plot in the first place.
        }
    }

    static boolean isCaptureBoxDrop(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.is(JSReviseItems.DINOSAUR_CAPTURE_CAGE.get())
                || stack.is(JSReviseItems.BROKEN_DINOSAUR_CAPTURE_BOX.get()));
    }

    static Optional<DropProjection> finishProjection(
            Vec3 globalPosition,
            Vec3 rotatedLocalVelocity,
            Vec3 craftPointVelocity,
            boolean globalStillInPlotGrid
    ) {
        if (globalStillInPlotGrid
                || !isFinite(globalPosition)
                || !isFinite(rotatedLocalVelocity)
                || !isFinite(craftPointVelocity)) {
            return Optional.empty();
        }
        Vec3 velocity = rotatedLocalVelocity.add(craftPointVelocity);
        return isFinite(velocity)
                ? Optional.of(new DropProjection(globalPosition, velocity))
                : Optional.empty();
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    record DropProjection(Vec3 position, Vec3 velocity) {
    }
}
