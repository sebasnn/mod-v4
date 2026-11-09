package dev.xmas.overlay.client;

import dev.xmas.overlay.XmasConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

/**
 * Knife trick state machine. While the trick key is HELD it plays a loop of butterfly-knife tricks (fast, like
 * real flippers); letting go finishes the current move quickly. With "auto tricks" one trick plays every 5 seconds.
 * With a knife in BOTH hands the loop also contains the "Clash": both knives meet in the middle and tap, with a sound.
 */
public final class TrickController {
	/** Extra motion added on top of the idle grip. Angles in degrees, offsets in view units. */
	public static final class Pose {
		public float dx, dy, dz, rx, ry, rz, close, u;        // knife. close: 0 = handles open, 1 = handles folded over the blade
		public float hdx, hdy, hdz, hrx, hry, hrz;            // hand (always 0 again when a move ends, so the hand glides home)
		void clear() { dx = dy = dz = rx = ry = rz = close = u = 0; hdx = hdy = hdz = hrx = hry = hrz = 0; }
	}

	private interface Move { void eval(double u, Pose p); }
	private record Def(String name, double ms, double[] taps, Move move) {}

	private static double ease(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
	private static double easeInOut(double t) { t = Math.max(0, Math.min(1, t)); return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; }
	private static double arc(double u) { return 4 * u * (1 - u); }
	private static double pulse(double u, double c, double w) { double d = (u - c) / w; return Math.exp(-d * d); }
	private static double sin(double x) { return Math.sin(x); }
	private static final double TAU = Math.PI * 2;

	private static final Def[] MOVES = {
			// 0: handles snap shut and open twice, wrist flicking
			new Def("Fan", 480, null, (u, p) -> {
				double k = (u * 2) % 1.0;
				double c = k < 0.5 ? ease(k * 2) : 1 - ease((k - 0.5) * 2);
				p.close = (float) Math.min(1.05, c * 1.05);
				p.rz = (float) (10 * sin(u * 2 * TAU));
				p.dy = (float) (0.03 * sin(u * 2 * TAU));
				p.hrz = (float) (14 * sin(u * 2 * TAU)); p.hdy = (float) (0.025 * sin(u * 2 * TAU)); p.hrx = (float) (6 * sin(u * TAU));
			}),
			// 1: two quick rolls round the blade axis
			new Def("Rollover", 560, null, (u, p) -> {
				p.ry = (float) (720 * easeInOut(u));
				p.rx = (float) (12 * sin(u * TAU)); p.rz = (float) (14 * sin(u * Math.PI));
				p.hry = (float) (26 * sin(u * 2 * TAU)); p.hrx = (float) (10 * sin(u * TAU)); p.hdx = (float) (0.03 * sin(u * TAU));
			}),
			// 2: toss up, double flip, handles fold mid-air, catch
			new Def("Aerial", 760, null, (u, p) -> {
				double a = arc(u);
				p.dy = (float) (0.42 * a); p.dz = (float) (0.12 * a);
				p.rx = (float) (-720 * easeInOut(u));
				p.close = (float) (ease((u - 0.08) / 0.2) * (1 - ease((u - 0.72) / 0.2)));
				p.hdy = (float) (0.2 * a); p.hdz = (float) (0.05 * a); p.hrx = (float) (-20 * sin(u * Math.PI));
			}),
			// 3: propeller spin in front of you, handles pumping
			new Def("Twirl", 640, null, (u, p) -> {
				p.rz = (float) (720 * easeInOut(u));
				p.close = (float) (0.5 - 0.5 * Math.cos(u * 3 * TAU));
				p.ry = (float) (22 * sin(u * 2 * TAU)); p.dz = (float) (0.06 * arc(u));
				p.hrz = (float) (30 * sin(u * 2 * TAU)); p.hdz = (float) (0.03 * arc(u)); p.hry = (float) (10 * sin(u * TAU));
			}),
			// 4: half-turns with the handles flipping every time
			new Def("Chaplin", 600, null, (u, p) -> {
				p.ry = (float) (360 * easeInOut(u));
				p.close = (float) Math.abs(sin(u * 2 * TAU));
				p.rx = (float) (16 * sin(u * TAU)); p.dy = (float) (0.05 * sin(u * 2 * TAU));
				p.hry = (float) (20 * sin(u * 2 * TAU)); p.hdy = (float) (0.02 * sin(u * 2 * TAU)); p.hrx = (float) (-8 * sin(u * TAU));
			}),
			// 5: rolls over the fingers with a wrist roll
			new Def("Zen roll", 720, null, (u, p) -> {
				p.rx = (float) (360 * easeInOut(u)); p.ry = (float) (120 * sin(u * TAU));
				p.close = (float) (0.3 * sin(u * Math.PI) * sin(u * Math.PI)); p.dz = (float) (0.07 * sin(u * TAU));
				p.hrx = (float) (22 * sin(u * TAU)); p.hdz = (float) (0.05 * arc(u)); p.hry = (float) (14 * sin(u * TAU));
			}),
			// 6: tumbling cartwheel
			new Def("Cartwheel", 700, null, (u, p) -> {
				double e = easeInOut(u);
				p.rz = (float) (360 * e); p.rx = (float) (-360 * e);
				p.close = (float) (0.5 - 0.5 * Math.cos(u * 2 * TAU));
				p.dy = (float) (0.1 * arc(u));
				p.hrz = (float) (20 * sin(u * TAU)); p.hrx = (float) (-14 * sin(u * TAU)); p.hdy = (float) (0.05 * arc(u));
			}),
			// 7: high toss, triple flip
			new Def("Triple flip", 880, null, (u, p) -> {
				double a = arc(u);
				p.dy = (float) (0.52 * a); p.dz = (float) (0.1 * a);
				p.rx = (float) (-1080 * easeInOut(u));
				p.close = (float) (ease((u - 0.1) / 0.15) * (1 - ease((u - 0.78) / 0.15)));
				p.hdy = (float) (0.24 * a); p.hrx = (float) (-26 * sin(u * Math.PI)); p.hdz = (float) (0.06 * a);
			}),
			// 8: ultra-fast triple roll
			new Def("Hand spin", 560, null, (u, p) -> {
				p.ry = (float) (1080 * easeInOut(u)); p.rz = (float) (18 * sin(u * TAU));
				p.close = (float) (0.18 * sin(u * Math.PI) * sin(u * Math.PI));
				double env = sin(u * Math.PI);
				p.hry = (float) (34 * sin(u * 3 * TAU) * env); p.hdx = (float) (0.04 * sin(u * 3 * TAU) * env);
			}),
			// 9: (two knives only) both knives meet in the middle and tap three times
			new Def("Clash", 1500, new double[] { 0.34, 0.52, 0.70 }, (u, p) -> {
				double g = ease(u / 0.22) * (1 - ease((u - 0.84) / 0.16));
				double tp = Math.min(1, pulse(u, 0.34, 0.03) + pulse(u, 0.52, 0.03) + pulse(u, 0.70, 0.03)) * g;
				p.dx = (float) (-0.34 * g - 0.06 * tp);
				p.dy = (float) (0.10 * g); p.dz = (float) (-0.05 * g);
				p.rz = (float) (22 * g + 8 * tp); p.rx = (float) (6 * g);
				p.hdx = (float) (p.dx * 0.85); p.hdy = (float) (p.dy * 0.85); p.hdz = (float) (p.dz * 0.85);
				p.hrz = (float) (10 * g + 5 * tp);
			}),
	};
	private static final int CLASH = 9, SOLO = 9;

	private static final Pose POSE = new Pose();
	private static boolean running;
	private static int index = -1, counter = 0;
	private static double progress;
	private static long lastMs = -1;

	private TrickController() {}

	public static Pose pose() { return POSE; }
	public static boolean isRunning() { return running; }
	public static String currentName() { return running ? MOVES[Math.max(0, index)].name() : ""; }

	/** True when a totem (the knife) is in the main hand OR the off hand. */
	public static boolean holdingKnife(MinecraftClient mc) {
		ClientPlayerEntity p = mc.player;
		return p != null && XmasConfig.get().customKnife
				&& (p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING));
	}

	/** True when there is a knife in both hands. */
	public static boolean holdingTwo(MinecraftClient mc) {
		ClientPlayerEntity p = mc.player;
		return p != null && XmasConfig.get().customKnife
				&& p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) && p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
	}

	/** The hand the effects follow: the main hand if it holds the knife, otherwise the off hand. */
	public static Hand activeHand(ClientPlayerEntity p) {
		return p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) ? Hand.MAIN_HAND : Hand.OFF_HAND;
	}

	/** +1 for a knife on the right side of the screen, -1 on the left (the off hand is the mirror image). */
	public static float sideOf(ClientPlayerEntity p, Hand hand) {
		Arm arm = hand == Hand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
		return arm == Arm.RIGHT ? 1f : -1f;
	}

	private static boolean single;          // a one-off trick started by "auto tricks"
	private static long autoNext;           // when the next automatic trick starts

	private static int nextIndex(boolean two) {
		counter++;
		if (two && counter % 3 == 1) return CLASH;
		return (counter * 7 / 3) % SOLO;     // walks through the solo moves without repeating the same one twice
	}

	private static void tapSound() {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(Identifier.ofVanilla("item.shield.block")), 1.9f, 0.55f));
			mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(Identifier.ofVanilla("block.amethyst_block.chime")), 2.0f, 0.25f));
		} catch (Throwable ignored) { }
	}

	/** Advance the state machine to 'now' (safe to call several times per frame). */
	public static void update(long nowMs, boolean keyDown) {
		MinecraftClient mc = MinecraftClient.getInstance();
		XmasConfig cfg = XmasConfig.get();
		boolean holding = holdingKnife(mc) && cfg.tricksEnabled && mc.currentScreen == null;
		boolean two = holdingTwo(mc);
		boolean keyWant = keyDown && holding;
		double dt = lastMs < 0 ? 0 : Math.min(100, nowMs - lastMs);
		if (nowMs == lastMs) return;
		lastMs = nowMs;

		if (!holding) { running = false; progress = 0; single = false; autoNext = nowMs + 2000; }
		if (!running && holding) {
			if (keyWant) { running = true; single = false; progress = 0; index = nextIndex(two); }
			else if (cfg.autoTricks && nowMs >= autoNext) { running = true; single = true; progress = 0; index = nextIndex(two); }
		}
		if (running) {
			if (index == CLASH && !two) { running = false; progress = 0; }          // the clash needs two knives
			else {
				double speed = keyWant || single ? 1.0 : 1.9;                         // let go of the key -> wrap up the move fast
				double before = progress;
				progress += dt * speed / MOVES[index].ms();
				double[] taps = MOVES[index].taps();
				if (taps != null) for (double t : taps) if (before < t && progress >= t) tapSound();
				if (progress >= 1) {
					progress = 0;
					if (keyWant) { single = false; index = nextIndex(two); }
					else {
						running = false;
						if (single) autoNext = nowMs + 5000;                          // one trick every 5 seconds
						single = false;
					}
				}
			}
		}
		POSE.clear();
		if (running) { MOVES[index].move().eval(progress, POSE); POSE.u = (float) progress; }
	}

	public static void reset() { running = false; progress = 0; lastMs = -1; POSE.clear(); }

	// ------------------------------------------------------------------ transform chain
	public interface Ops {
		void translate(float x, float y, float z);
		void rotX(float deg);
		void rotY(float deg);
		void rotZ(float deg);
		void scale(float s);
	}

	public static final float REST_X = 0.56f, REST_Y = -0.52f, REST_Z = -0.72f;     // vanilla item rest position
	private static final float GRIP_X = -0.10f, GRIP_Y = 0.17f, GRIP_Z = 0.05f;      // where the hand holds the knife
	private static final float IDLE_RX = -32f, IDLE_RZ = 14f;                         // idle tilt: blade up, leaning forward + inward
	public static final float BASE_SIZE = 0.42f;
	public static final float GRIP_LOCAL_Y = 4.5f / 16f;                               // middle of the handles, in blocks

	/** The same chain is replayed on a MatrixStack for drawing and on a JOML matrix for particle positions. */
	public static void apply(Ops o, float side, Pose p, long nowMs) {
		XmasConfig cfg = XmasConfig.get();
		float sway = (float) Math.sin(nowMs / 900.0) * (p.close == 0 && !running ? 1.4f : 0.4f);
		o.translate(side * (GRIP_X + cfg.gripX / 100f + p.dx), GRIP_Y + cfg.gripY / 100f + p.dy, GRIP_Z + cfg.gripZ / 100f + p.dz);
		o.rotZ(side * p.rz);
		o.rotX(p.rx);
		o.rotZ(side * IDLE_RZ + sway);
		o.rotX(IDLE_RX);
		o.rotY(side * p.ry);
		o.scale(BASE_SIZE * cfg.knifeSize / 100f);
		o.translate(0, GRIP_LOCAL_Y, 0);
	}
}
