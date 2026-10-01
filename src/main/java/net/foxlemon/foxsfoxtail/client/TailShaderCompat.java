package net.foxlemon.foxsfoxtail.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

// Iris shadow transforms are for drawing, not world collision. Detect those passes via its API.
// Reflection keeps Iris optional; the API lookup is cached rather than repeated every frame.
final class TailShaderCompat {
    private static final BooleanSupplier SHADOW_PASS = findShadowPass();

    private TailShaderCompat() {}

    static boolean isShadowPass() {
        return SHADOW_PASS.getAsBoolean();
    }

    private static BooleanSupplier findShadowPass() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Method shadowPass = api.getMethod("isRenderingShadowPass");
            return new BooleanSupplier() {
                private boolean failed;

                @Override
                public boolean getAsBoolean() {
                    // If the API fails, keep drawing but stop using uncertain collision samples.
                    if (failed) return true;
                    try {
                        return (boolean) shadowPass.invoke(instance);
                    } catch (ReflectiveOperationException | LinkageError exception) {
                        failed = true;
                        LogUtils.getLogger().warn("Fox's Foxtail cannot query the Iris shadow pass; render-based physics sampling disabled", exception);
                        return true;
                    }
                }
            };
        } catch (ClassNotFoundException exception) {
            return () -> false;
        } catch (ReflectiveOperationException | LinkageError exception) {
            LogUtils.getLogger().warn("Fox's Foxtail cannot access the Iris shadow-pass API; render-based physics sampling disabled", exception);
            return () -> true;
        }
    }
}
