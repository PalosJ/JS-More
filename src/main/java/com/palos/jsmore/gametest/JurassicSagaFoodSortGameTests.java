package com.palos.jsmore.gametest;

import com.palos.jsmore.JSMore;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jp.jurassicsaga.server.animal.JSAnimals;
import jp.jurassicsaga.server.animal.animals.obj.JSAnimal;
import jp.jurassicsaga.server.animal.entity.obj.bases.JSAnimalBase;
import jp.jurassicsaga.server.animal.entity.obj.diet.Diet;
import jp.jurassicsaga.server.animal.entity.obj.modules.obj.JSMetabolismModule;
import jp.jurassicsaga.server.animal.entity.obj.tasks.metabolism.JSFindFoodTask;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JSMore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JurassicSagaFoodSortGameTests {
    private static final int REQUIRED_SUBJECT_COUNT = 2;
    private static final int CANDIDATE_COUNT = 64;
    private static final int SEARCH_ROUNDS_PER_SUBJECT = 3;
    private static final int SEARCH_INTERVAL_TICKS = 20;

    private JurassicSagaFoodSortGameTests() {
    }

    @GameTest(template = "profile_compatibility", timeoutTicks = 180)
    public static void foodTaskSortsDenseDynamicCandidatesAcrossMultipleRounds(GameTestHelper helper) {
        if (!isFoodSortMixinApplied()) {
            helper.fail("Jurassic Saga food-sort Mixin was not applied to the real JSFindFoodTask class");
            return;
        }

        List<Subject> subjects = findCapableSubjects(helper);
        if (subjects.size() < REQUIRED_SUBJECT_COUNT) {
            subjects.forEach(subject -> subject.animal().discard());
            helper.fail("Expected at least two distinct hunger-enabled Jurassic Saga species with edible items");
            return;
        }

        AtomicInteger subjectIndex = new AtomicInteger();
        AtomicReference<Fixture> activeFixture = new AtomicReference<>();
        helper.onEachTick(() -> {
            Fixture fixture = activeFixture.get();
            if (fixture == null) {
                int index = subjectIndex.getAndIncrement();
                if (index >= REQUIRED_SUBJECT_COUNT) {
                    helper.succeed();
                    return;
                }
                fixture = createFixture(helper, subjects.get(index));
                if (fixture == null) {
                    subjects.forEach(subject -> subject.animal().discard());
                    return;
                }
                activeFixture.set(fixture);
            }

            long elapsed = helper.getLevel().getGameTime() - fixture.startGameTime;
            if (elapsed == 0L || elapsed % SEARCH_INTERVAL_TICKS != 0L) {
                return;
            }
            int liveCandidates = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(fixture.center).inflate(8.0D),
                    Entity::isAlive
            ).size();
            if (liveCandidates < 48) {
                cleanup(fixture.subject.animal(), fixture.candidates);
                subjects.forEach(subject -> subject.animal().discard());
                helper.fail("Dense food candidate fixture dropped below 48 live entities");
                return;
            }

            try {
                fixture.task.findTargets(
                        96.0F,
                        (fixture.rounds & 1) == 0 ? null : fixture.subject.animal().position()
                );
            } catch (IllegalArgumentException exception) {
                cleanup(fixture.subject.animal(), fixture.candidates);
                subjects.forEach(subject -> subject.animal().discard());
                helper.fail("Jurassic Saga food sort rejected its comparator contract: " + exception.getMessage());
                return;
            }

            fixture.rounds++;
            if (fixture.rounds >= SEARCH_ROUNDS_PER_SUBJECT) {
                cleanup(fixture.subject.animal(), fixture.candidates);
                activeFixture.set(null);
            }
        });
    }

    private static boolean isFoodSortMixinApplied() {
        for (Method method : JSFindFoodTask.class.getDeclaredMethods()) {
            if (method.getName().contains("jsmore$sortFoodCandidatesWithStableScores")) {
                return true;
            }
        }
        return false;
    }

    private static List<Subject> findCapableSubjects(GameTestHelper helper) {
        List<Subject> subjects = new ArrayList<>(REQUIRED_SUBJECT_COUNT);
        Set<EntityType<?>> selectedTypes = new HashSet<>();
        for (JSAnimal<?> registeredAnimal : JSAnimals.getAnimals()) {
            Entity entity = registeredAnimal.getEntityType().get().create(helper.getLevel());
            if (!(entity instanceof JSAnimalBase animal)) {
                if (entity != null) {
                    entity.discard();
                }
                continue;
            }
            JSMetabolismModule metabolism = animal.getModules().getMetabolismModule();
            Diet diet = metabolism == null ? null : metabolism.getDiet();
            ItemStack selectedFood = ItemStack.EMPTY;
            if (metabolism != null && metabolism.isHungerEnabled() && diet != null) {
                for (ItemStack food : diet.getAllItems()) {
                    if (food != null && !food.isEmpty() && diet.canEatItem(food)) {
                        selectedFood = food.copy();
                        break;
                    }
                }
            }
            if (!selectedFood.isEmpty()
                    && selectedTypes.add(animal.getType())
                    && subjects.size() < REQUIRED_SUBJECT_COUNT) {
                subjects.add(new Subject(animal, selectedFood));
            } else {
                animal.discard();
            }
        }
        return subjects;
    }

    private static Fixture createFixture(GameTestHelper helper, Subject subject) {
        JSAnimalBase animal = subject.animal();
        List<ItemEntity> candidates = new ArrayList<>(CANDIDATE_COUNT);
        BlockPos center = helper.absolutePos(new BlockPos(8, 4, 8));
        animal.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        animal.setNoAi(true);
        if (!helper.getLevel().addFreshEntity(animal)) {
            animal.discard();
            helper.fail("Could not add a dynamically selected Jurassic Saga animal");
            return null;
        }

        for (int index = 0; index < CANDIDATE_COUNT; index++) {
            int xOffset = index % 8 - 4;
            int zOffset = index / 8 - 4;
            ItemStack stack = subject.food().copy();
            stack.setCount(1);
            ItemEntity candidate = new ItemEntity(
                    helper.getLevel(),
                    center.getX() + 0.5D + xOffset,
                    center.getY() + 0.25D,
                    center.getZ() + 0.5D + zOffset,
                    stack
            );
            candidate.setDeltaMovement(Vec3.ZERO);
            candidate.setNoGravity(true);
            if (!helper.getLevel().addFreshEntity(candidate)) {
                cleanup(animal, candidates);
                helper.fail("Could not add all dense food candidates");
                return null;
            }
            candidates.add(candidate);
        }

        return new Fixture(
                subject,
                new JSFindFoodTask(animal),
                candidates,
                center,
                helper.getLevel().getGameTime()
        );
    }

    private static void cleanup(JSAnimalBase animal, List<ItemEntity> candidates) {
        candidates.forEach(Entity::discard);
        animal.discard();
    }

    private record Subject(JSAnimalBase animal, ItemStack food) {
    }

    private static final class Fixture {
        private final Subject subject;
        private final JSFindFoodTask task;
        private final List<ItemEntity> candidates;
        private final BlockPos center;
        private final long startGameTime;
        private int rounds;

        private Fixture(
                Subject subject,
                JSFindFoodTask task,
                List<ItemEntity> candidates,
                BlockPos center,
                long startGameTime
        ) {
            this.subject = subject;
            this.task = task;
            this.candidates = candidates;
            this.center = center;
            this.startGameTime = startGameTime;
        }
    }
}
