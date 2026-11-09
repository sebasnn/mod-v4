package dev.xmas.overlay.mixin;

import dev.xmas.overlay.XmasConfig;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Zoom out": a wider field of view for the world. The hand layer is compensated in the item renderer. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void xmas$zoomOut(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		int z = XmasConfig.get().zoomOut;
		if (z >= 100) return;
		double orig = cir.getReturnValue();
		double out = 2.0 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(orig) / 2.0) / (z / 100.0)));
		cir.setReturnValue((float) Math.min(170.0, out));
	}
}
