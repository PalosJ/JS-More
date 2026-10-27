package com.palos.jsmore.client.overlay;

import com.palos.jsmore.network.AnimalObservationPayload;
import com.palos.jsmore.network.AnimalObservationRequestPayload;
import com.palos.jsmore.system.observation.CaptureCageObservationSnapshot;
import com.palos.jsmore.system.observation.DinosaurObservationSnapshot;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import jp.jurassicsaga.server.entity.animal.entity.obj.bases.JSAnimalBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** One observed target and one in-flight nonce; old worlds and recycled entity IDs cannot match. */
public final class ClientAnimalObservationCache {
    private static Object session;
    private static AnimalObservationRequestPayload pending;
    private static UUID target;
    private static DinosaurObservationSnapshot snapshot;
    private static long receivedAt;
    private static long nextRequest;
    private static long lastTime = Long.MIN_VALUE;

    private ClientAnimalObservationCache() { }

    public static Optional<DinosaurObservationSnapshot> getOrRequest(JSAnimalBase animal) {
        Level level = animal.level();
        return level.isClientSide ? getOrRequest(level, level.dimension().location(), animal.getId(), animal.getUUID(),
                level.getGameTime(), PacketDistributor::sendToServer) : Optional.empty();
    }

    static Optional<DinosaurObservationSnapshot> getOrRequest(Object world, ResourceLocation dimension, int id, UUID uuid,
                                                              long now, Consumer<AnimalObservationRequestPayload> sender) {
        if (session != world || now < lastTime) clearCache();
        session = world;
        lastTime = now;
        if (!uuid.equals(target)) {
            target = uuid;
            pending = null;
            snapshot = null;
            nextRequest = Math.min(nextRequest, saturatedAdd(now, 5L));
        }
        if (nextRequest != Long.MAX_VALUE && now >= nextRequest) {
            pending = new AnimalObservationRequestPayload(id, target, dimension, UUID.randomUUID());
            sender.accept(pending);
            nextRequest = saturatedAdd(now, 20L);
        }
        return snapshot != null && now >= receivedAt && now - receivedAt <= 40L ? Optional.of(snapshot) : Optional.empty();
    }

    public static void remember(Level level, AnimalObservationPayload payload) {
        if (!(level.getEntity(payload.request().entityId()) instanceof JSAnimalBase animal)) return;
        remember(level, level.dimension().location(), animal.getUUID(), level.getGameTime(), payload);
    }

    static void remember(Object world, ResourceLocation dimension, UUID actualUuid, long now, AnimalObservationPayload payload) {
        if (session != world || pending == null || !pending.equals(payload.request()) || now < lastTime
                || !dimension.equals(pending.dimension()) || !actualUuid.equals(pending.entityUuid())) return;
        snapshot = CaptureCageObservationSnapshot.deserializeNBT(payload.snapshotTag())
                .map(CaptureCageObservationSnapshot::observation).orElse(null);
        receivedAt = now;
        pending = null;
    }

    public static void invalidate(JSAnimalBase animal) {
        if (animal != null && animal.getUUID().equals(target)) stopObserving();
    }

    public static void stopObserving() { target = null; pending = null; snapshot = null; }

    public static void clearCache() {
        stopObserving(); session = null; receivedAt = 0L; nextRequest = 0L; lastTime = Long.MIN_VALUE;
    }

    private static long saturatedAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }
}
