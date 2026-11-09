package dev.xmas.overlay.client;

import dev.xmas.overlay.Safe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import dev.xmas.overlay.XmasConfig;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class XmasOverlayClient implements ClientModInitializer {
	public static final String MOD_ID = "xmasoverlay";

	public static KeyBinding trickKey;
	public static KeyBinding configKey;
	private static long lastWarn;

	@Override
	public void onInitializeClient() {
		XmasConfig.load();

		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
		trickKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.xmasoverlay.trick", GLFW.GLFW_KEY_G, category));
		configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.xmasoverlay.config", GLFW.GLFW_KEY_J, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			try {
				while (configKey.wasPressed()) {
					if (mc.currentScreen == null) mc.setScreen(new ConfigScreen(null));
				}
				while (trickKey.wasPressed()) {
					if (mc.player == null || mc.currentScreen != null) continue;
					if (!TrickController.holdingKnife(mc)) {
						long now = System.currentTimeMillis();
						if (now - lastWarn > 1500) {
							lastWarn = now;
							mc.player.sendMessage(Text.literal("You can't do that - hold the knife in your hand to do tricks!"), false);
						}
					}
				}
			} catch (Throwable t) {
				Safe.fail("keys", t);
			}
		});
	}
}
