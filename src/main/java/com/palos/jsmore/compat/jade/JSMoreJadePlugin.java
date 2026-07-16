package com.palos.jsmore.compat.jade;

import com.palos.jsmore.server.block.DinosaurCaptureCageBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("jade")
public final class JSMoreJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addTooltipCollectedCallback(100, JSMoreJadeTooltipFilter::filterAnimalTooltip);
        registration.registerBlockComponent(JSMoreJadeCaptureBoxProvider.INSTANCE, DinosaurCaptureCageBlock.class);
    }
}
