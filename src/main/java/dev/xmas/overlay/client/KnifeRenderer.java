package dev.xmas.overlay.client;

import dev.xmas.overlay.Skin;
import dev.xmas.overlay.XmasConfig;
import dev.xmas.overlay.mixin.HeldItemRendererInvoker;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/** Draws the first-person butterfly knife (blade + two handles) together with your hand. */
public final class KnifeRenderer {
	private KnifeRenderer() {}

	private static final float PIVOT_SHIFT = 0.25f;   // model pivot sits 4 units above the model centre

	private static final class StackOps implements TrickController.Ops {
		final MatrixStack m;
		StackOps(MatrixStack m) { this.m = m; }
		public void translate(float x, float y, float z) { m.translate(x, y, z); }
		public void rotX(float d) { m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(d)); }
		public void rotY(float d) { m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(d)); }
		public void rotZ(float d) { m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(d)); }
		public void scale(float s) { m.scale(s, s, s); }
	}

	/**
	 * Keeps hand and items where they were when the camera zooms out: the world gets a wider field of view
	 * (so everything shrinks towards the centre), the hand layer is stretched by the same amount.
	 */
	public static void zoomCompensation(MatrixStack m) {
		int z = XmasConfig.get().zoomOut;
		if (z >= 100) return;
		float k = 100f / z;
		m.scale(k, k, 1f);
	}

	/** Scale hand + item around the spot where the item rests, so it keeps its place in the corner. */
	public static void sizeAroundRest(MatrixStack m, float side) {
		int sz = XmasConfig.get().itemSize;
		if (sz == 100) return;
		float s = sz / 100f;
		m.translate(side * TrickController.REST_X, TrickController.REST_Y, TrickController.REST_Z);
		m.scale(s, s, s);
		m.translate(-side * TrickController.REST_X, -TrickController.REST_Y, -TrickController.REST_Z);
	}

	public static void render(HeldItemRenderer hir, AbstractClientPlayerEntity player, Hand hand, float swing, float equip,
			MatrixStack m, OrderedRenderCommandQueue q, int light) {
		XmasConfig cfg = XmasConfig.get();
		HeldItemRendererInvoker inv = (HeldItemRendererInvoker) hir;
		Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == Arm.RIGHT ? 1f : -1f;
		long now = System.currentTimeMillis();
		TrickController.update(now, XmasOverlayClient.trickKey != null && XmasOverlayClient.trickKey.isPressed());
		TrickController.Pose pose = TrickController.pose();
		Skin skin = Skin.current();

		if (cfg.showHand && !player.isInvisible()) {
			m.push();
			try {
				zoomCompensation(m);
				if (TrickController.isRunning()) {
					// the hand does its own flicks (see the moves) and is back at its normal place when a move ends
					m.translate(side * pose.hdx, pose.hdy, pose.hdz);
					m.translate(side * TrickController.REST_X, TrickController.REST_Y, TrickController.REST_Z);
					m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.hrx));
					m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * pose.hry));
					m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * pose.hrz));
					m.translate(-side * TrickController.REST_X, -TrickController.REST_Y, -TrickController.REST_Z);
				}
				sizeAroundRest(m, side);
				inv.xmas$renderArmHoldingItem(m, q, light, equip, swing, arm);
			} finally { m.pop(); }
		}

		m.push();
		try {
			zoomCompensation(m);
			inv.xmas$applyEquipOffset(m, arm, equip);
			inv.xmas$applySwingOffset(m, arm, swing);
			if (cfg.itemShiftEnabled) {
				float d = cfg.itemShift / 100f;
				m.translate(-side * 0.45f * d, 0.30f * d, -0.25f * d);
			}
			float s = cfg.itemSize / 100f;
			m.scale(s, s, s);
			TrickController.apply(new StackOps(m), side, pose, now);
			ItemDisplayContext ctx = arm == Arm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
			float angle = 180f * pose.close;
			// blade + pins
			m.push();
			m.translate(0, -PIVOT_SHIFT, 0);
			inv.xmas$renderItem(player, skin.part(0), ctx, m, q, light);
			m.pop();
			// handle A swings round one side, handle B round the other
			for (int i = 1; i <= 2; i++) {
				m.push();
				m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i == 1 ? angle : -angle));
				m.translate(0, -PIVOT_SHIFT, 0);
				inv.xmas$renderItem(player, skin.part(i), ctx, m, q, light);
				m.pop();
			}
		} finally {
			m.pop();
		}
	}
}
