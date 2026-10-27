package com.palos.jsmore.compat.jade;

public final class JadeAnimalTooltipPolicy {
    public record Fields(boolean health, boolean sex, boolean genes) {
        public static final Fields NONE = new Fields(false, false, false);
    }
    private static Object frame;
    private static Object level;
    private static java.util.UUID target;
    private static Fields fields = Fields.NONE;
    private JadeAnimalTooltipPolicy() {
    }

    public static void clearFrame() {
        frame = null; level = null; target = null; fields = Fields.NONE;
    }

    public static void recordRendered(Object graphics, Object world, java.util.UUID entity, Fields visible) {
        frame = graphics; level = world; target = entity; fields = visible;
    }

    public static Fields visibleFields(Object graphics, Object world, java.util.UUID entity) {
        return !com.palos.jsmore.config.JSMoreClientConfig.showFullGogglesInformation()
                && frame == graphics && level == world && entity.equals(target) ? fields : Fields.NONE;
    }
}
