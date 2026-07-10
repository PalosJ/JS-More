package com.palos.jsrevise.mixin;

import com.palos.jsrevise.compat.travelers.TravelersHandler1211BytecodePatch;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class JSReviseMixinPlugin implements IMixinConfigPlugin {
    private static final String SERVER_MARKER =
            "com.palos.jsrevise.mixin.server.TravelersHandler1211ServerMixin";
    private static final String TARGET_CLASS = "collinvht.travelers.handler.v1211.Handler1211";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
        if (!SERVER_MARKER.equals(mixinClassName)) {
            return;
        }
        if (!TARGET_CLASS.equals(targetClassName)) {
            throw new IllegalStateException(
                    "JS-revise Travelers server marker targeted unexpected class " + targetClassName
            );
        }
        if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.SERVER) {
            throw new IllegalStateException("JS-revise Travelers bytecode patch may only run on the server side");
        }
        TravelersHandler1211BytecodePatch.apply(targetClass);
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
    }
}
