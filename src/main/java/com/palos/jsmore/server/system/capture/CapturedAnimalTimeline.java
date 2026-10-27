package com.palos.jsmore.server.system.capture;

import com.palos.jsmore.server.registry.JSMoreAttachments;
import com.palos.jsmore.server.system.anesthetic.AnestheticData;
import com.palos.jsmore.server.system.anesthetic.DinosaurAnestheticSystem;
import java.util.OptionalLong;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.entity.animal.entity.obj.info.AnimalGrowthStage;
import jp.jurassicsaga.server.entity.animal.entity.obj.modules.obj.JSGrowthStageModule;
import jp.jurassicsaga.server.entity.animal.entity.obj.modules.obj.JSMetabolismModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Advances a detached snapshot between events, never between individual unloaded ticks. */
final class CapturedAnimalTimeline {
    private static final long CARE_INTERVAL = 20L;
    private static final long VITAL_INTERVAL = 200L;
    // At most 100 reserves, 64 pre-existing doses, growth/zero/break boundaries.
    private static final int MAX_EVENTS = 1024;

    record Result(int durability, int durabilityRemainder, int vitalRemainder,
                  DinosaurCaptureSupplies supplies, OptionalLong lastCare) { }

    private CapturedAnimalTimeline() { }

    static Result settle(JSAnimalBase animal, CapturedDinosaurData original,
                         DinosaurCaptureSupplies supplies, long end) {
        long cursor = Math.min(end, original.lastSettledGameTime());
        boolean rollback = original.lastSettledGameTime() > end;
        long oldCare = original.vitals().lastAutoCareGameTime().orElse(cursor - CARE_INTERVAL);
        long phase = rollback || oldCare > end ? end : oldCare;
        long firstCare = rollback || oldCare > end ? add(end, CARE_INTERVAL) : add(phase, CARE_INTERVAL);
        int durability = original.durability();
        int wearRemainder = original.durabilityRemainderTicks();
        int vitalRemainder = original.vitals().capturedRelativeTicks();
        AnestheticData drug = animal.getData(JSMoreAttachments.ANESTHETIC);
        JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
        JSGrowthStageModule growth = animal.getModules().getGrowthStageModule();
        CapturedGrowthAccess growthAccess = (CapturedGrowthAccess) growth;
        boolean hasGrowthAnchor = original.entityNbt().contains("js.growthstage.lastseen", Tag.TAG_LONG);
        growthAccess.jsmore$settleGrowthAt(hasGrowthAnchor ? cursor : end);
        long lastActualCare = Long.MIN_VALUE;

        for (int events = 0; events < MAX_EVENTS; events++) {
            long eligibleCare = nextCare(animal, metabolism, drug, supplies, cursor, vitalRemainder);
            long careAt = alignCare(Math.max(firstCare, eligibleCare), phase);
            if (lastActualCare == cursor && careAt == cursor) careAt = add(cursor, CARE_INTERVAL);
            if (durability > 0 && careAt == cursor && cursor <= end) {
                supplies = care(animal, metabolism, drug, supplies, cursor);
                // An additional injection can accelerate a pending dose to this very instant.
                CompoundTag relative = drug.serializeRelativeNBT(cursor);
                drug.deserializeRelativeNBT(null, relative, cursor, cursor);
                lastActualCare = cursor;
            }
            if (cursor >= end) {
                long lastPass = phase <= end ? phase + (end - phase) / CARE_INTERVAL * CARE_INTERVAL : end;
                return new Result(durability, wearRemainder, vitalRemainder, supplies,
                        OptionalLong.of(Math.max(0L, lastPass)));
            }

            long next = end;
            if (durability > 0 && !supplies.isEmpty()) {
                eligibleCare = nextCare(animal, metabolism, drug, supplies, cursor, vitalRemainder);
                careAt = alignCare(Math.max(firstCare, eligibleCare), phase);
                if (careAt <= cursor) careAt = add(cursor, CARE_INTERVAL);
                next = Math.min(next, careAt);
            }
            long pendingDelay = drug.pendingDelayTicks(cursor);
            if (drug.pendingDoseCount() > 0 && pendingDelay > 0) next = Math.min(next, add(cursor, pendingDelay));
            next = Math.min(next, zeroEvent(cursor, metabolism.isHungerEnabled(), metabolism.getHunger(), vitalRemainder));
            next = Math.min(next, zeroEvent(cursor, metabolism.isThirstEnabled(), metabolism.getThirst(), vitalRemainder));
            if (hasGrowthAnchor) next = Math.min(next, add(cursor, growthBoundary(animal, growth)));
            CompoundTag relative = drug.serializeRelativeNBT(cursor);
            long exposed = AnestheticProtectionIntervals.unprotectedTicks(relative, cursor, cursor, next);
            if (durability > 0 && exposed >= (long) durability * CARE_INTERVAL - wearRemainder) {
                // Locate the break event without walking the potentially very long interval.
                long required = (long) durability * CARE_INTERVAL - wearRemainder;
                long low = cursor + 1L, high = next;
                while (low < high) {
                    long middle = low + (high - low) / 2L;
                    if (AnestheticProtectionIntervals.unprotectedTicks(relative, cursor, cursor, middle) >= required) high = middle;
                    else low = middle + 1L;
                }
                next = low;
                exposed = required;
            }
            if (next <= cursor) throw new IllegalStateException("Capture timeline did not advance");
            long elapsed = next - cursor;
            long wear = add(exposed, wearRemainder);
            durability = (int) Math.max(0L, durability - Math.min(durability, wear / CARE_INTERVAL));
            wearRemainder = (int) (wear % CARE_INTERVAL);
            boolean starving = metabolism.isHungerEnabled() && metabolism.getHunger() <= 0
                    || metabolism.isThirstEnabled() && metabolism.getThirst() <= 0;
            double previousHealth = animal.getHealth();
            double healing = hasGrowthAnchor ? growthAccess.jsmore$settleGrowthAt(next, false) : 0.0D;
            // Each segment has a constant starvation/growth state. Combine their rates before
            // clamping so one long settlement agrees with many small settlements at full health.
            double health = previousHealth + healing - (starving ? elapsed / 1200.0D : 0.0D);
            animal.setHealth((float) Math.min(animal.getMaxHealth(), Math.max(Math.min(1.0D, previousHealth), health)));
            long total = add(elapsed, vitalRemainder);
            long drain = total / VITAL_INTERVAL;
            if (metabolism.isHungerEnabled()) metabolism.setHunger((int) Math.max(0L, metabolism.getHunger() - drain));
            if (metabolism.isThirstEnabled()) metabolism.setThirst((int) Math.max(0L, metabolism.getThirst() - drain));
            vitalRemainder = (int) (total % VITAL_INTERVAL);
            drug.deserializeRelativeNBT(null, relative, cursor, next);
            cursor = next;
        }
        // Caller retains the original authoritative carrier and supplies on any failure.
        throw new IllegalStateException("Capture timeline exceeded its bounded event budget");
    }

    private static long nextCare(JSAnimalBase animal, JSMetabolismModule metabolism, AnestheticData drug,
                                 DinosaurCaptureSupplies supplies, long now, int remainder) {
        long next = Long.MAX_VALUE;
        var diet = CapturedDinosaurVitals.resolveReserveDiet(animal, metabolism);
        if (metabolism.isHungerEnabled() && DinosaurCaptureService.selectFoodSupply(diet, supplies) != null)
            next = Math.min(next, thresholdEvent(now, metabolism.getHunger(), metabolism.getMaxHunger(), remainder));
        if (metabolism.isThirstEnabled() && supplies.water() > 0)
            next = Math.min(next, thresholdEvent(now, metabolism.getThirst(), metabolism.getMaxThirst(), remainder));
        if (supplies.anesthetic() > 0 && drug.pendingDoseCount() < 4)
            next = Math.min(next, add(now, Math.max(0L, drug.remainingTicks(now) - 1199L)));
        return next;
    }

    private static DinosaurCaptureSupplies care(JSAnimalBase animal, JSMetabolismModule metabolism,
                                                AnestheticData drug, DinosaurCaptureSupplies supplies, long now) {
        if (metabolism.isHungerEnabled()
                && DinosaurCaptureService.isBelowAutoCareThreshold(metabolism.getHunger(), metabolism.getMaxHunger())) {
            var type = DinosaurCaptureService.selectFoodSupply(CapturedDinosaurVitals.resolveReserveDiet(animal, metabolism), supplies);
            if (type != null) {
                metabolism.setHunger((int) Math.min(metabolism.getMaxHunger(), (long) metabolism.getHunger() + 8500L));
                supplies = supplies.consume(type);
            }
        }
        if (metabolism.isThirstEnabled() && supplies.water() > 0
                && DinosaurCaptureService.isBelowAutoCareThreshold(metabolism.getThirst(), metabolism.getMaxThirst())) {
            long restored = ((long) metabolism.getMaxThirst() + 9L) / 10L;
            metabolism.setThirst((int) Math.min(metabolism.getMaxThirst(), metabolism.getThirst() + restored));
            supplies = supplies.consume(DinosaurCaptureSupplies.Type.WATER);
        }
        if (supplies.anesthetic() > 0 && drug.pendingDoseCount() < 4 && drug.remainingTicks(now) < 1200L
                && DinosaurAnestheticSystem.tryApplyCapturedInjection(animal, now))
            supplies = supplies.consume(DinosaurCaptureSupplies.Type.ANESTHETIC);
        return supplies;
    }

    private static long thresholdEvent(long now, int value, int maximum, int remainder) {
        if (maximum <= 0) return Long.MAX_VALUE;
        long target = ((long) maximum * 80L - 1L) / 100L;
        return add(now, Math.max(0L, ((long) value - target) * VITAL_INTERVAL - remainder));
    }

    private static long zeroEvent(long now, boolean enabled, int value, int remainder) {
        return enabled && value > 0 ? add(now, (long) value * VITAL_INTERVAL - remainder) : Long.MAX_VALUE;
    }

    private static long growthBoundary(JSAnimalBase animal, JSGrowthStageModule growth) {
        double rate = (double) growth.getGrowthMultiplier() * growth.getGrowthMultiplier();
        double missing = growth.getMaxGrowth() - growth.getGrowthProgress();
        if (!Double.isFinite(rate) || rate <= 0.0D || missing <= 0.0D) return Long.MAX_VALUE;
        long ticks = Math.max(1L, (long) Math.ceil(missing / rate));
        var baby = animal.getAnimal().getAnimalAttributes().getGeneticProperties().getBabyData();
        if (baby == null) return Long.MAX_VALUE;
        if (growth.getGrowthStage() != AnimalGrowthStage.ADULT) {
            double threshold = baby.getMaxGrowth() == -1 ? growth.getMaxGrowth() : baby.getMaxGrowth();
            ticks = Math.min(ticks, Math.max(1L, (long) Math.floor((threshold - growth.getGrowthProgress()) / rate) + 1L));
        }
        return ticks;
    }

    private static long alignCare(long time, long phase) {
        if (time == Long.MAX_VALUE) return time;
        long remainder = Math.floorMod(time - phase, CARE_INTERVAL);
        return remainder == 0 ? time : add(time, CARE_INTERVAL - remainder);
    }

    private static long add(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
