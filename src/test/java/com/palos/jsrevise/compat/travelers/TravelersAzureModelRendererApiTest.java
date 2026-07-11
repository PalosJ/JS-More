package com.palos.jsrevise.compat.travelers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

class TravelersAzureModelRendererApiTest {
    private static final String RENDER_DESCRIPTOR =
            "(Lcollinvht/travelers/client/azure/common/render/AzRendererPipelineContext;Z)V";
    private static final String ANIMATION_DEFINITION_DESCRIPTOR =
            "Lcollinvht/travelers/server/animal/obj/animation/TravelersAnimationDefinition;";

    @Test
    void realTravelers071RendererKeepsTheTargetedRenderApi() throws IOException {
        ClassNode renderer = readClass(
                "collinvht.travelers.client.render.animal.azure.TravelersAzureModelRenderer"
        );

        assertTrue(renderer.methods.stream().anyMatch(method ->
                "render".equals(method.name)
                        && RENDER_DESCRIPTOR.equals(method.desc)
                        && (method.access & Opcodes.ACC_PUBLIC) != 0
        ));
        assertTrue(renderer.fields.stream().anyMatch(field ->
                "animalAnimator".equals(field.name)
                        && "Lcollinvht/travelers/client/render/animation/entity/obj/TravelersClientAnimator;"
                        .equals(field.desc)
        ));
        assertTrue(renderer.fields.stream().anyMatch(field ->
                "hasAnimator".equals(field.name) && "Z".equals(field.desc)
        ));
    }

    @Test
    void realTravelers071LocatorAndPublicStopApisRemainAvailable() throws IOException {
        ClassNode locator = readClass(
                "collinvht.travelers.server.animal.obj.locator.ResourceLocator"
        );
        assertTrue(locator.methods.stream().anyMatch(method ->
                "getAnimationLocation".equals(method.name)
                        && "(Lcollinvht/travelers/server/animal/entity/SmartAnimalBase;)"
                        .concat("Lnet/minecraft/resources/ResourceLocation;")
                        .equals(method.desc)
                        && (method.access & Opcodes.ACC_PUBLIC) != 0
        ));

        ClassNode animationDefinition = readClass(
                "collinvht.travelers.server.animal.obj.animation.TravelersAnimationDefinition"
        );
        assertTrue(animationDefinition.methods.stream().anyMatch(method ->
                "stopForEntity".equals(method.name)
                        && "(Lcollinvht/travelers/server/animal/entity/SmartAnimalBase;)V".equals(method.desc)
                        && (method.access & Opcodes.ACC_PUBLIC) != 0
        ));

        ClassNode jsAnimations = readClass(
                "jp.jurassicsaga.server.animal.animations.obj.JSAnimations"
        );
        assertTrue(jsAnimations.fields.stream().anyMatch(field ->
                "IDLE".equals(field.name)
                        && ANIMATION_DEFINITION_DESCRIPTOR.equals(field.desc)
                        && (field.access & Opcodes.ACC_PUBLIC) != 0
                        && (field.access & Opcodes.ACC_STATIC) != 0
        ));
    }

    private static ClassNode readClass(String className) throws IOException {
        Class<?> dependencyClass;
        try {
            dependencyClass = Class.forName(
                    className,
                    false,
                    Thread.currentThread().getContextClassLoader()
            );
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Required dependency class is unavailable: " + className, exception);
        }
        String simpleClassFile = dependencyClass.getSimpleName() + ".class";
        try (InputStream stream = dependencyClass.getResourceAsStream(simpleClassFile)) {
            assertNotNull(stream, "Required dependency class bytes are unavailable: " + className);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
