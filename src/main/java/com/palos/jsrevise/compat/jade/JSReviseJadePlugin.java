package com.palos.jsrevise.compat.jade;

import com.palos.jsrevise.server.block.DinosaurCaptureCageBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("jade")
public final class JSReviseJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addTooltipCollectedCallback(100, JSReviseJadeTooltipFilter::filterAnimalTooltip);
        registration.registerBlockComponent(JSReviseJadeCaptureBoxProvider.INSTANCE, DinosaurCaptureCageBlock.class);
    }
}
