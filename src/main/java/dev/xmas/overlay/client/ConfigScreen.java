package dev.xmas.overlay.client;

import dev.xmas.overlay.Safe;
import dev.xmas.overlay.Skin;
import dev.xmas.overlay.TotemSkin;
import dev.xmas.overlay.XmasConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Black + purple settings screen: glowing panel, drifting stars, animated tabs, switches, sliders,
 * a skin picker with photos, hover hints, click ripples and key rebinding. Everything is drawn by hand and
 * the mouse is polled, so it does not depend on vanilla widgets.
 */
public class ConfigScreen extends Screen {
	private static final int W = 560, H = 352, CX = 128, CY = 56, CW = 420;
	private static final int PANEL = 0xFF0A0810, SIDE = 0xFF0E0B16;
	// theme colours: they follow the colour of the knife (or totem) you have equipped
	private int P0 = 0xFF4B22A0, P1 = 0xFF9B5CFF, P2 = 0xFFD39BFF, CARD = 0xFF171126, CARD_H = 0xFF241A40, knobOn = 0xFFFFFFFF;
	private float[] themeCur;
	private String knifeCat = "christmas";
	private static final int TEXT = 0xFFF3ECFF, MUTED = 0xFF8D80AE, OFF = 0xFF332B4A;
	private static final String[] TABS = { "Knives", "Totems", "Tricks", "Hands", "Camera", "Keys" };
	private static final int[] TAB_ICON = { 0, 5, 1, 2, 3, 4 };

	private final Screen parent;
	private int tab = 0;
	private float tabY = 0;
	private long openMs = -1, lastMs;
	private float ga = 0, contentFade = 1, tabSlide = 0;
	private boolean prevDown, firstFrame = true;
	private Slider drag;
	private KeyBinding capturing;
	private String hoverSkin, hint = "";
	private final List<Ctl> ctls = new ArrayList<>();
	private final List<float[]> ripples = new ArrayList<>();   // x, y, age
	private final float[] tabHover = new float[TABS.length];
	private final float[] cardHover = new float[40];

	public ConfigScreen(Screen parent) {
		super(Text.literal("Christmas Overlay"));
		this.parent = parent;
	}

	@Override protected void init() { buildTab(); }

	// ------------------------------------------------------------------ drawing helpers
	private int a(int argb) {
		int al = (int) (((argb >>> 24) & 0xFF) * ga);
		return (al << 24) | (argb & 0xFFFFFF);
	}
	private static float approach(float cur, float target, float rate, float dt) {
		float d = target - cur; float step = rate * dt;
		return Math.abs(d) <= step ? target : cur + Math.signum(d) * step;
	}
	private static int mixc(int c1, int c2, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
		int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
		int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
	private static int alpha(int rgb, float al) {
		return ((int) (Math.max(0, Math.min(1, al)) * 255) << 24) | (rgb & 0xFFFFFF);
	}
	private void rrect(DrawContext c, int x, int y, int w, int h, int r, int col) {
		col = a(col);
		if (w <= 0 || h <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) { c.fill(x, y, x + w, y + h, col); return; }
		c.fill(x + r, y, x + w - r, y + h, col);
		c.fill(x, y + r, x + r, y + h - r, col);
		c.fill(x + w - r, y + r, x + w, y + h - r, col);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
			c.fill(x + inset, y + i, x + r, y + i + 1, col);
			c.fill(x + w - r, y + i, x + w - inset, y + i + 1, col);
			c.fill(x + inset, y + h - 1 - i, x + r, y + h - i, col);
			c.fill(x + w - r, y + h - 1 - i, x + w - inset, y + h - i, col);
		}
	}
	/** Soft glow: several translucent rounded rectangles growing outwards. */
	private void glow(DrawContext c, int x, int y, int w, int h, int r, int rgb, float strength) {
		for (int i = 5; i >= 1; i--)
			rrect(c, x - i, y - i, w + 2 * i, h + 2 * i, r + i, alpha(rgb, strength * 0.07f * (6 - i) / 5f));
	}
	private void text(DrawContext c, String s, int x, int y, int col) { c.drawText(textRenderer, s, x, y, a(col), false); }
	private void textC(DrawContext c, String s, int cx, int y, int col) { text(c, s, cx - textRenderer.getWidth(s) / 2, y, col); }
	private void textR(DrawContext c, String s, int rx, int y, int col) { text(c, s, rx - textRenderer.getWidth(s), y, col); }
	private void bigText(DrawContext c, String s, int x, int y, float sc, int col) {
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(x, y);
		c.getMatrices().scale(sc, sc);
		text(c, s, 0, 0, col);
		c.getMatrices().popMatrix();
	}
	private void icon(DrawContext c, int kind, int cx, int cy, int col) {
		switch (kind) {
			case 0 -> { c.fill(cx - 1, cy - 4, cx + 1, cy + 4, a(col)); c.fill(cx - 3, cy - 2, cx + 3, cy + 2, a(col)); }        // diamond
			case 1 -> { c.fill(cx - 4, cy - 1, cx + 4, cy + 1, a(col)); c.fill(cx - 1, cy - 4, cx + 1, cy + 4, a(col)); }        // sparkle
			case 2 -> { c.fill(cx - 3, cy - 1, cx + 4, cy + 4, a(col)); c.fill(cx - 3, cy - 4, cx - 2, cy - 1, a(col)); c.fill(cx - 1, cy - 5, cx, cy - 1, a(col)); c.fill(cx + 1, cy - 4, cx + 2, cy - 1, a(col)); } // hand
			case 3 -> { c.fill(cx - 4, cy - 2, cx + 4, cy + 3, a(col)); c.fill(cx - 2, cy - 3, cx + 1, cy - 2, a(col)); c.fill(cx - 1, cy - 1, cx + 1, cy + 1, a(0xFF0C0814)); } // camera
			case 5 -> { c.fill(cx - 2, cy - 4, cx + 2, cy, a(col)); c.fill(cx - 4, cy - 1, cx + 4, cy + 1, a(col)); c.fill(cx - 2, cy, cx + 2, cy + 4, a(col)); }   // totem
			default -> { c.fill(cx - 4, cy - 3, cx + 4, cy + 3, a(col)); c.fill(cx - 3, cy - 2, cx + 3, cy + 2, a(0xFF0C0814)); c.fill(cx - 2, cy - 1, cx + 2, cy, a(col)); }  // keyboard
		}
	}

	// ------------------------------------------------------------------ controls
	private abstract class Ctl {
		int x, y, w, h = 24; float hover; String hintText = "";
		boolean in(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
		abstract void draw(DrawContext c, int mx, int my, float dt);
		boolean click(int mx, int my) { return false; }
		Ctl hint(String s) { hintText = s; return this; }
		void hoverCheck(int mx, int my, float dt) {
			boolean in = in(mx, my) && !(this instanceof Label);
			hover = approach(hover, (in || drag == this) ? 1 : 0, 9, dt);
			if (in && !hintText.isEmpty()) hint = hintText;
		}
	}

	private class Toggle extends Ctl {
		final String label; final BooleanSupplier get; final Consumer<Boolean> set; float k;
		Toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
			this.x = x; this.y = y; this.w = w; this.label = label; this.get = get; this.set = set; this.k = get.getAsBoolean() ? 1 : 0;
		}
		void draw(DrawContext c, int mx, int my, float dt) {
			hoverCheck(mx, my, dt);
			k = approach(k, get.getAsBoolean() ? 1 : 0, 8, dt);
			if (hover > 0.01) glow(c, x, y, w, h, 5, P1, hover * 0.8f);
			rrect(c, x, y, w, h, 5, mixc(CARD, CARD_H, hover));
			text(c, label, x + 9, y + 8, mixc(0xFFC9BEE6, TEXT, Math.max(k, hover)));
			int px = x + w - 36, py = y + 6;
			rrect(c, px, py, 28, 12, 6, mixc(OFF, P1, k));
			if (k > 0.05) glow(c, px, py, 28, 12, 6, P1, k * 0.9f);
			int kx = px + 2 + (int) (16 * k);
			rrect(c, kx, py + 2, 8, 8, 4, mixc(0xFFB9AEDB, knobOn, k));
		}
		boolean click(int mx, int my) {
			if (!in(mx, my)) return false;
			set.accept(!get.getAsBoolean()); XmasConfig.save(); return true;
		}
	}

	private class Slider extends Ctl {
		final String label; final int min, max; final IntSupplier get; final IntConsumer set; final IntFunction<String> fmt; float shown;
		Slider(int x, int y, int w, String label, int min, int max, IntSupplier get, IntConsumer set, IntFunction<String> fmt) {
			this.x = x; this.y = y; this.w = w; this.label = label; this.min = min; this.max = max; this.get = get; this.set = set; this.fmt = fmt;
			this.shown = (get.getAsInt() - min) / (float) (max - min);
		}
		void draw(DrawContext c, int mx, int my, float dt) {
			hoverCheck(mx, my, dt);
			float target = (get.getAsInt() - min) / (float) (max - min);
			shown = approach(shown, target, 7, dt);
			if (hover > 0.01) glow(c, x, y, w, h, 5, P1, hover * 0.8f);
			rrect(c, x, y, w, h, 5, mixc(CARD, CARD_H, hover));
			int fw = Math.max(0, (int) ((w - 2) * shown));
			if (fw > 3) {
				rrect(c, x + 1, y + 1, fw, h - 2, 4, mixc(P0, P1, 0.45f + 0.4f * hover));
				c.fill(x + 3, y + 2, x + fw - 1, y + 3, a(alpha(0xFFFFFF, 0.18f)));
				rrect(c, x + fw - 2, y + 3, 4, h - 6, 2, 0xFFFFFFFF);
			}
			text(c, label, x + 9, y + 8, TEXT);
			textR(c, fmt.apply(get.getAsInt()), x + w - 9, y + 8, hover > 0.5 ? P2 : TEXT);
		}
		boolean click(int mx, int my) {
			if (!in(mx, my)) return false;
			drag = this; dragTo(mx); return true;
		}
		void dragTo(int mx) {
			float f = Math.max(0, Math.min(1, (mx - x - 4) / (float) (w - 8)));
			set.accept(min + Math.round(f * (max - min)));
		}
	}

	private class KeyButton extends Ctl {
		final String label; final KeyBinding bind;
		KeyButton(int x, int y, int w, String label, KeyBinding bind) { this.x = x; this.y = y; this.w = w; this.label = label; this.bind = bind; }
		void draw(DrawContext c, int mx, int my, float dt) {
			hoverCheck(mx, my, dt);
			if (hover > 0.01) glow(c, x, y, w, h, 5, P1, hover * 0.8f);
			rrect(c, x, y, w, h, 5, mixc(CARD, CARD_H, hover));
			text(c, label, x + 9, y + 8, TEXT);
			boolean cap = capturing == bind;
			String v = cap ? "Press a key..." : bind.getBoundKeyLocalizedText().getString();
			int bw = Math.max(58, textRenderer.getWidth(v) + 18);
			float pulse = cap ? 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0;
			if (cap) glow(c, x + w - bw - 5, y + 3, bw, h - 6, 5, P1, 0.6f + pulse * 0.4f);
			rrect(c, x + w - bw - 5, y + 3, bw, h - 6, 5, cap ? mixc(P1, P2, pulse) : mixc(CARD, P1, 0.3f));
			textC(c, v, x + w - bw / 2 - 5, y + 8, 0xFFFFFFFF);
		}
		boolean click(int mx, int my) { if (!in(mx, my)) return false; capturing = bind; return true; }
	}

	private class Label extends Ctl {
		final String s; final int col;
		Label(int x, int y, String s, int col) { this.x = x; this.y = y; this.s = s; this.col = col; this.w = 0; this.h = 0; }
		void draw(DrawContext c, int mx, int my, float dt) { text(c, s, x, y, col); }
	}

	private class SkinCard extends Ctl {
		final Skin skin; final int idx;
		SkinCard(int x, int y, Skin skin, int idx) { this.x = x; this.y = y; this.w = 46; this.h = 46; this.skin = skin; this.idx = idx; }
		void draw(DrawContext c, int mx, int my, float dt) {
			boolean hov = in(mx, my);
			boolean sel = XmasConfig.get().skin.equals(skin.id);
			cardHover[idx] = approach(cardHover[idx], hov ? 1 : 0, 10, dt);
			if (hov) { hoverSkin = skin.id; hint = skin.name + " - " + skin.blurb; }
			float hv = cardHover[idx];
			float s = 1f + 0.12f * hv;
			int cx = x + w / 2, cy = y + h / 2;
			c.getMatrices().pushMatrix();
			c.getMatrices().translate(cx, cy);
			c.getMatrices().scale(s, s);
			c.getMatrices().translate(-cx, -cy);
			float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 380.0);
			if (sel) glow(c, x - 2, y - 2, w + 4, h + 4, 5, P1, 0.7f + 0.5f * pulse);
			else if (hv > 0.01) glow(c, x - 2, y - 2, w + 4, h + 4, 5, P1, hv * 0.9f);
			int border = sel ? mixc(P1, P2, pulse) : mixc(0xFF2B2145, 0xFFFFFFFF, hv * 0.5f);
			rrect(c, x - 2, y - 2, w + 4, h + 4, 5, border);
			Identifier tex = Identifier.of("xmasoverlay", "textures/gui/skin/" + skin.id + ".png");
			c.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, h, 192, 192, 192, 192, 0x00FFFFFF | ((int) (255 * ga) << 24));
			if (sel) { rrect(c, x + w - 13, y + h - 13, 11, 11, 5, P1); text(c, "v", x + w - 10, y + h - 12, 0xFFFFFFFF); }
			c.getMatrices().popMatrix();
		}
		boolean click(int mx, int my) {
			if (!in(mx, my)) return false;
			XmasConfig.get().skin = skin.id; XmasConfig.save(); return true;
		}
	}

	private class TotemCard extends Ctl {
		final TotemSkin skin; final int idx;
		TotemCard(int x, int y, TotemSkin skin, int idx) { this.x = x; this.y = y; this.w = 34; this.h = 34; this.skin = skin; this.idx = idx; }
		void draw(DrawContext c, int mx, int my, float dt) {
			boolean hov = in(mx, my);
			boolean sel = XmasConfig.get().totemSkin.equals(skin.id);
			cardHover[idx] = approach(cardHover[idx], hov ? 1 : 0, 10, dt);
			if (hov) { hoverSkin = skin.id; hint = skin.name + " totem (used when the butterfly knife is off)"; }
			float hv = cardHover[idx];
			float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 380.0);
			int cx = x + w / 2, cy = y + h / 2;
			float s = 1f + 0.12f * hv;
			c.getMatrices().pushMatrix();
			c.getMatrices().translate(cx, cy); c.getMatrices().scale(s, s); c.getMatrices().translate(-cx, -cy);
			if (sel) glow(c, x - 2, y - 2, w + 4, h + 4, 5, P1, 0.7f + 0.5f * pulse);
			else if (hv > 0.01) glow(c, x - 2, y - 2, w + 4, h + 4, 5, P1, hv * 0.9f);
			rrect(c, x - 2, y - 2, w + 4, h + 4, 5, sel ? mixc(P1, P2, pulse) : mixc(0xFF2B2145, 0xFFFFFFFF, hv * 0.5f));
			rrect(c, x, y, w, h, 4, 0xFF0C0A12);
			Identifier tex = Identifier.of("xmasoverlay", "textures/item/totem/" + skin.id + ".png");
			c.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x + 1, y + 1, 0f, 0f, w - 2, h - 2, 16, 16, 16, 16, 0x00FFFFFF | ((int) (255 * ga) << 24));
			c.getMatrices().popMatrix();
		}
		boolean click(int mx, int my) {
			if (!in(mx, my)) return false;
			XmasConfig.get().totemSkin = skin.id; XmasConfig.save(); return true;
		}
	}

	private class Pill extends Ctl {
		final String label, cat; float k;
		Pill(int x, int y, int w, String label, String cat) { this.x = x; this.y = y; this.w = w; this.h = 22; this.label = label; this.cat = cat; this.k = knifeCat.equals(cat) ? 1 : 0; }
		void draw(DrawContext c, int mx, int my, float dt) {
			hoverCheck(mx, my, dt);
			k = approach(k, knifeCat.equals(cat) ? 1 : 0, 9, dt);
			if (k > 0.05) glow(c, x, y, w, h, 11, P1, k);
			rrect(c, x, y, w, h, 11, mixc(mixc(CARD, CARD_H, hover), P1, k * 0.85f));
			textC(c, label, x + w / 2, y + 7, mixc(0xFFC9BEE6, 0xFFFFFFFF, Math.max(k, hover)));
		}
		boolean click(int mx, int my) {
			if (!in(mx, my)) return false;
			if (!knifeCat.equals(cat)) { knifeCat = cat; buildTab(); }
			return true;
		}
	}

	// ------------------------------------------------------------------ tabs
	private void buildTab() {
		ctls.clear(); drag = null; hoverSkin = null; hint = "";
		XmasConfig c = XmasConfig.get();
		int L = CX, R = CX + 214, HW = 206, FW = CW;
		switch (tab) {
			case 0 -> {
				ctls.add(new Toggle(L, CY, HW, "Butterfly knife", () -> c.customKnife, v -> c.customKnife = v).hint("ON: your totem is a butterfly knife. OFF: it is a normal-looking totem (see the Totems tab)."));
				ctls.add(new Pill(R + 8, CY + 1, 94, "Christmas", "christmas"));
				ctls.add(new Pill(R + 108, CY + 1, 94, "Classic", "classic"));
				List<Skin> list = new ArrayList<>();
				for (Skin sk : Skin.ALL) if (sk.category.equals(knifeCat)) list.add(sk);
				for (int i = 0; i < list.size(); i++)
					ctls.add(new SkinCard(L + 148 + (i % 5) * 54, CY + 38 + (i / 5) * 54 + 3, list.get(i), Skin.ALL.indexOf(list.get(i)) % 40));
			}
			case 1 -> {
				ctls.add(new Label(L, CY + 2, "Normal-looking totems in 26 colours. Used when the butterfly knife is OFF.", MUTED));
				for (int i = 0; i < TotemSkin.ALL.size(); i++)
					ctls.add(new TotemCard(L + 148 + (i % 7) * 38, CY + 28 + (i / 7) * 38 + 3, TotemSkin.ALL.get(i), i));
				ctls.add(new Label(L, CY + 196, "Totems cannot do tricks - only the butterfly knife can.", MUTED));
			}
			case 2 -> {
				ctls.add(new Toggle(L, CY, HW, "Tricks (hold key)", () -> c.tricksEnabled, v -> c.tricksEnabled = v).hint("Hold the trick key with a knife in your hand."));
				ctls.add(new Toggle(R, CY, HW, "Auto tricks", () -> c.autoTricks, v -> c.autoTricks = v).hint("One trick by itself every 5 seconds while you hold the knife."));
				ctls.add(new Toggle(L, CY + 32, HW, "Show my hand", () -> c.showHand, v -> c.showHand = v).hint("Draws your hand holding the knife."));
				ctls.add(new Slider(R, CY + 32, HW, "Knife size", 50, 200, () -> c.knifeSize, v -> c.knifeSize = v, v -> v + "%").hint("Size of the knife only."));
				ctls.add(new Label(L, CY + 76, "Knife in one hand: 9 different tricks.", MUTED));
				ctls.add(new Label(L, CY + 90, "Knife in BOTH hands: the two knives also clash in the middle with a sound.", MUTED));
				ctls.add(new Label(L, CY + 104, "Knife in your off hand works too (it is the mirror image).", MUTED));
			}
			case 3 -> {
				ctls.add(new Slider(L, CY, FW, "Swing speed (only you see it)", 1, 100, () -> c.swingSpeed, v -> c.swingSpeed = v, v -> v + "%").hint("100% is normal. Lower is slower. Hit, mine, use and place animations."));
				ctls.add(new Slider(L, CY + 32, FW, "Item + hand size (any item)", 1, 200, () -> c.itemSize, v -> c.itemSize = v, v -> v + "%").hint("Size of your hand and the item in it. 100% is normal."));
				ctls.add(new Toggle(L, CY + 64, HW, "Move held items inward", () -> c.itemShiftEnabled, v -> c.itemShiftEnabled = v).hint("Pulls items away from the screen corner."));
				ctls.add(new Slider(R, CY + 64, HW, "Inward amount", 0, 100, () -> c.itemShift, v -> c.itemShift = v, v -> v + "%"));
				ctls.add(new Label(L, CY + 104, "Knife position in your hand (only if hand and knife do not line up):", MUTED));
				ctls.add(new Slider(L, CY + 120, FW, "Left / right", -50, 50, () -> c.gripX, v -> c.gripX = v, v -> "" + v));
				ctls.add(new Slider(L, CY + 148, FW, "Up / down", -50, 50, () -> c.gripY, v -> c.gripY = v, v -> "" + v));
				ctls.add(new Slider(L, CY + 176, FW, "Forward / back", -50, 50, () -> c.gripZ, v -> c.gripZ = v, v -> "" + v));
			}
			case 4 -> {
				ctls.add(new Slider(L, CY, FW, "Camera zoom out", 40, 100, () -> c.zoomOut, v -> c.zoomOut = v, v -> String.format("%.2fx", v / 100f)).hint("Wider view like the 0.5x lens of a phone. Your hand and items stay where they are."));
				ctls.add(new Label(L, CY + 36, "100% = normal. Only the world zooms out, not your hand or items.", MUTED));
			}
			default -> {
				ctls.add(new KeyButton(L, CY, FW, "Trick key (hold it to do tricks)", XmasOverlayClient.trickKey));
				ctls.add(new KeyButton(L, CY + 32, FW, "Open this menu", XmasOverlayClient.configKey));
				ctls.add(new Label(L, CY + 74, "Click a button, then press the key you want. Esc cancels.", MUTED));
				ctls.add(new Label(L, CY + 88, "Empty hand or a normal totem: the trick key only shows a message in chat.", MUTED));
			}
		}
		contentFade = 0; tabSlide = 1;
	}

	// ------------------------------------------------------------------ render
	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		if (openMs < 0) openMs = now;
		float dt = Math.min(0.1f, Math.max(0.001f, (now - (lastMs == 0 ? now - 16 : lastMs)) / 1000f));
		lastMs = now;
		float open = Math.min(1f, (now - openMs) / 320f);
		open = 1 - (1 - open) * (1 - open) * (1 - open);
		ga = open;
		contentFade = approach(contentFade, 1, 5, dt);
		updateTheme(dt);
		tabSlide = approach(tabSlide, 0, 5, dt);

		MinecraftClient mc = MinecraftClient.getInstance();
		Window win = mc.getWindow();
		double rawX = mc.mouse.getX() * win.getScaledWidth() / (double) win.getWidth();
		double rawY = mc.mouse.getY() * win.getScaledHeight() / (double) win.getHeight();
		boolean down = GLFW.glfwGetMouseButton(win.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (firstFrame) { prevDown = down; firstFrame = false; }

		float ps = Math.min(1f, Math.min(width / (W + 20f), height / (H + 20f)));
		float sc = ps * (0.94f + 0.06f * open);
		float px = (width - W * sc) / 2f, py = (height - H * sc) / 2f + (1 - open) * 12;
		int mx = (int) ((rawX - px) / sc), my = (int) ((rawY - py) / sc);

		if (down && !prevDown) onClick(mx, my);
		if (down && drag != null) drag.dragTo(mx);
		if (!down && prevDown) { drag = null; XmasConfig.save(); }
		prevDown = down;

		// dark world + purple vignette
		int dim = (int) (0xB8 * open);
		ctx.fill(0, 0, width, height, dim << 24 | 0x04020A);
		ctx.fillGradient(0, 0, width, height / 2, ((int) (0x55 * open) << 24) | (P0 & 0xFFFFFF), 0x00000000);
		ctx.fillGradient(0, height / 2, width, height, 0x00000000, ((int) (0x44 * open) << 24) | (P0 & 0xFFFFFF));

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(px, py);
		ctx.getMatrices().scale(sc, sc);
		hint = "";
		hoverSkin = null;
		try {
			drawPanel(ctx, mx, my, dt, now);
		} finally {
			ctx.getMatrices().popMatrix();
		}
	}

	private void updateTheme(float dt) {
		XmasConfig cfg = XmasConfig.get();
		int target = cfg.customKnife ? Skin.current().theme : TotemSkin.current().theme;
		float[] t = { ((target >> 16) & 255) / 255f, ((target >> 8) & 255) / 255f, (target & 255) / 255f };
		if (themeCur == null) themeCur = t.clone();
		else for (int i = 0; i < 3; i++) themeCur[i] += (t[i] - themeCur[i]) * Math.min(1f, dt * 6f);
		float lum = 0.299f * themeCur[0] + 0.587f * themeCur[1] + 0.114f * themeCur[2];
		int base = 0xFF000000 | ((int) (themeCur[0] * 255) << 16) | ((int) (themeCur[1] * 255) << 8) | (int) (themeCur[2] * 255);
		if (lum < 0.34f) base = mixc(base, 0xFFFFFFFF, (0.34f - lum) * 1.7f);     // keep dark themes (obsidian...) readable
		P1 = base; P0 = mixc(0xFF05030B, base, 0.5f); P2 = mixc(base, 0xFFFFFFFF, 0.45f);
		CARD = mixc(0xFF110E18, base, 0.08f); CARD_H = mixc(0xFF110E18, base, 0.22f);
		knobOn = lum > 0.7f ? 0xFF2B2B38 : 0xFFFFFFFF;
	}

	private static float hash(float v) { float s = (float) Math.sin(v * 12.9898) * 43758.5453f; return s - (float) Math.floor(s); }

	private void drawPanel(DrawContext c, int mx, int my, float dt, long now) {
		float t = now / 1000f;
		// outer glow + panel + inner gradient
		float pulse = 0.65f + 0.35f * (float) Math.sin(t * 1.6);
		glow(c, 0, 0, W, H, 12, P1, 0.9f * pulse);
		rrect(c, 0, 0, W, H, 12, 0xFF05030B);
		rrect(c, 1, 1, W - 2, H - 2, 11, PANEL);
		c.fillGradient(2, 14, W - 2, 150, a(alpha(P1, 0.19f)), 0x00000000);
		c.fillGradient(2, H - 90, W - 2, H - 14, 0x00000000, a(alpha(P0, 0.17f)));

		// drifting stars
		for (int i = 0; i < 70; i++) {
			float r1 = hash(i * 1.37f), r2 = hash(i * 7.11f + 3), r3 = hash(i * 3.73f + 9);
			float x = (r1 * W + t * (2 + 6 * r3)) % W;
			float y = (r2 * H + t * (1 + 3 * r3) * 0.4f) % H;
			float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * (1 + 3 * r3) + i));
			int sz = r3 > 0.85f ? 2 : 1;
			int col = r1 > 0.5f ? P2 : 0xFFFFFFFF;
			c.fill((int) x, (int) y, (int) x + sz, (int) y + sz, a(alpha(col, 0.55f * tw)));
		}

		// header
		for (int i = 0; i < W - 40; i += 3) {
			float f = i / (float) (W - 40);
			float wave = 0.5f + 0.5f * (float) Math.sin(t * 2.2 - f * 9);
			c.fill(20 + i, 0, 23 + i, 2, a(mixc(P0, P2, wave)));
		}
		String title = "CHRISTMAS OVERLAY";
		int tx = 22;
		for (int i = 0; i < title.length(); i++) {
			float wave = 0.5f + 0.5f * (float) Math.sin(t * 2.4 - i * 0.5);
			String ch = title.substring(i, i + 1);
			bigText(c, ch, tx, 14, 1.6f, mixc(P1, 0xFFFFFFFF, wave * 0.8f));
			tx += (int) (textRenderer.getWidth(ch) * 1.6f) + 1;
		}
		text(c, "client settings", 22, 34, MUTED);
		boolean xin = mx >= W - 34 && mx < W - 8 && my >= 8 && my < 30;
		rrect(c, W - 34, 10, 22, 18, 5, xin ? mixc(CARD, P1, 0.55f) : CARD);
		textC(c, "x", W - 23, 15, xin ? 0xFFFFFFFF : MUTED);

		// sidebar
		rrect(c, 10, 50, 104, H - 60, 8, SIDE);
		int target = 56 + tab * 36;
		if (Math.abs(tabY - target) > 120) tabY = target;
		tabY += (target - tabY) * Math.min(1f, dt * 13f);
		glow(c, 14, (int) tabY, 96, 30, 6, P1, 0.9f);
		rrect(c, 14, (int) tabY, 96, 30, 6, mixc(0xFF0C0A12, P1, 0.3f));
		rrect(c, 14, (int) tabY + 7, 3, 16, 1, P2);
		for (int i = 0; i < TABS.length; i++) {
			int ty = 56 + i * 36;
			boolean in = mx >= 14 && mx < 110 && my >= ty && my < ty + 30;
			tabHover[i] = approach(tabHover[i], in ? 1 : 0, 9, dt);
			if (i != tab && tabHover[i] > 0.01) rrect(c, 14, ty, 96, 30, 6, alpha(0xFFFFFF, 0.07f * tabHover[i]));
			int col = i == tab ? 0xFFFFFFFF : mixc(MUTED, TEXT, tabHover[i]);
			icon(c, TAB_ICON[i], 29, ty + 15, i == tab ? P2 : mixc(0xFF6B6578, P1, tabHover[i]));
			text(c, TABS[i], 42 + (int) (tabHover[i] * 3), ty + 11, col);
		}
		boolean rin = mx >= 14 && mx < 110 && my >= H - 54 && my < H - 28 - 0;
		rrect(c, 14, H - 54, 96, 24, 6, rin ? mixc(CARD, P1, 0.45f) : CARD);
		textC(c, "Reset all", 62, H - 46, rin ? 0xFFFFFFFF : MUTED);

		// content
		float keep = ga;
		ga = ga * contentFade;
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(tabSlide * 26f, 0);
		if (tab == 0 || tab == 1) drawSkinPreview(c, now);
		for (Ctl ctl : ctls) ctl.draw(c, mx, my, dt);
		c.getMatrices().popMatrix();
		ga = keep;

		// hint bar
		rrect(c, CX - 4, H - 36, CW + 8, 24, 6, 0xFF120C20);
		text(c, hint.isEmpty() ? "Hover something for a hint." : hint, CX + 6, H - 28, hint.isEmpty() ? 0xFF5A4E78 : 0xFFC9BEE6);

		// click ripples
		Iterator<float[]> it = ripples.iterator();
		while (it.hasNext()) {
			float[] rp = it.next();
			rp[2] += dt;
			if (rp[2] > 0.5f) { it.remove(); continue; }
			float k = rp[2] / 0.5f;
			int rad = (int) (4 + k * 22);
			rrect(c, (int) rp[0] - rad, (int) rp[1] - rad, rad * 2, rad * 2, rad, alpha(P2, (1 - k) * 0.28f));
		}
	}

	private void drawSkinPreview(DrawContext c, long now) {
		if (tab == 1) {
			TotemSkin t = TotemSkin.byId(hoverSkin != null ? hoverSkin : XmasConfig.get().totemSkin);
			float pu = 0.5f + 0.5f * (float) Math.sin(now / 700.0);
			glow(c, CX - 2, CY - 2, 136, 136, 6, P1, 0.7f + 0.4f * pu);
			rrect(c, CX - 2, CY - 2, 136, 136, 6, mixc(P0, P2, pu));
			rrect(c, CX, CY, 132, 132, 5, 0xFF0C0A12);
			c.drawTexture(RenderPipelines.GUI_TEXTURED, Identifier.of("xmasoverlay", "textures/item/totem/" + t.id + ".png"), CX + 10, CY + 10, 0f, 0f, 112, 112, 16, 16, 16, 16, 0x00FFFFFF | ((int) (255 * ga) << 24));
			text(c, t.name + " totem", CX, CY + 140, TEXT);
			return;
		}
		Skin s = Skin.byId(hoverSkin != null ? hoverSkin : XmasConfig.get().skin);
		float pulse = 0.5f + 0.5f * (float) Math.sin(now / 700.0);
		glow(c, CX - 2, CY - 2, 136, 136, 6, P1, 0.7f + 0.4f * pulse);
		rrect(c, CX - 2, CY - 2, 136, 136, 6, mixc(P0, P2, pulse));
		Identifier tex = Identifier.of("xmasoverlay", "textures/gui/skin/" + s.id + ".png");
		c.drawTexture(RenderPipelines.GUI_TEXTURED, tex, CX, CY, 0f, 0f, 132, 132, 192, 192, 192, 192, 0x00FFFFFF | ((int) (255 * ga) << 24));
		text(c, s.name, CX, CY + 140, TEXT);
		String[] words = s.blurb.split(" ");
		StringBuilder line = new StringBuilder(); int yy = CY + 152;
		for (String wd : words) {
			if (textRenderer.getWidth(line + wd) > 132 && line.length() > 0) { text(c, line.toString().trim(), CX, yy, MUTED); yy += 10; line.setLength(0); }
			line.append(wd).append(' ');
		}
		if (line.length() > 0) text(c, line.toString().trim(), CX, yy, MUTED);
	}

	private void onClick(int mx, int my) {
		ripples.add(new float[] { mx, my, 0f });
		if (capturing != null) { capturing = null; return; }
		if (mx >= W - 34 && mx < W - 8 && my >= 8 && my < 30) { close(); return; }
		for (int i = 0; i < TABS.length; i++) {
			int ty = 56 + i * 36;
			if (mx >= 14 && mx < 110 && my >= ty && my < ty + 30) { if (tab != i) { tab = i; buildTab(); } return; }
		}
		if (mx >= 14 && mx < 110 && my >= H - 54 && my < H - 28) { XmasConfig.reset(); Safe.enableAll(); buildTab(); return; }
		for (Ctl t : ctls) if (t.click(mx, my)) return;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (capturing != null) {
			if (input.isEscape()) { capturing = null; return true; }
			capturing.setBoundKey(InputUtil.fromKeyCode(input));
			KeyBinding.updateKeysByCode();
			MinecraftClient.getInstance().options.write();
			capturing = null;
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void close() {
		XmasConfig.save();
		MinecraftClient.getInstance().setScreen(parent);
	}
}
