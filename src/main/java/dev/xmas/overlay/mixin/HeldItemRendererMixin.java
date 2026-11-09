package dev.xmas.overlay.mixin;

import dev.xmas.overlay.Safe;
import dev.xmas.overlay.XmasConfig;
import dev.xmas.overlay.client.KnifeRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
	/** true when we pushed a matrix at the start of this hand and still have to pop it at the end. */
	private boolean xmas$pushed;

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void xmas$held(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
			float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		xmas$pushed = false;
		XmasConfig cfg = XmasConfig.get();
		try {
			// the knife (main hand or off hand - the off hand is the mirror image)
			if (cfg.customKnife && Safe.ok("knife") && item.isOf(Items.TOTEM_OF_UNDYING)) {
				try {
					KnifeRenderer.render((HeldItemRenderer) (Object) this, player, hand, swingProgress, equipProgress, matrices, queue, light);
					ci.cancel();
					return;
				} catch (Throwable t) {
					Safe.fail("knife", t);
				}
			}
			boolean shift = cfg.itemShiftEnabled && cfg.itemShift > 0;
			if (!shift && cfg.itemSize == 100 && cfg.zoomOut >= 100) return;
			// Everything we change is undone again at the end of this hand, so the other hand is never affected.
			matrices.push();
			xmas$pushed = true;
			Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
			float side = arm == Arm.RIGHT ? 1f : -1f;
			KnifeRenderer.zoomCompensation(matrices);
			if (shift) {
				float d = cfg.itemShift / 100f;
				matrices.translate(-side * 0.45f * d, 0.30f * d, -0.25f * d);
			}
			KnifeRenderer.sizeAroundRest(matrices, side);   // hand and item together
		} catch (Throwable t) {
			Safe.fail("hands", t);
		}
	}

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
	private void xmas$heldEnd(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
			float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		if (xmas$pushed) {
			xmas$pushed = false;
			matrices.pop();
		}
	}
}
