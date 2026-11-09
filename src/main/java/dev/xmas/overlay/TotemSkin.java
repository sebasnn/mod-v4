package dev.xmas.overlay;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A recoloured normal totem (shown when the butterfly knife is switched off). These cannot do tricks. */
public final class TotemSkin {
	public static final List<TotemSkin> ALL = new ArrayList<>();

	public final String id, name;
	public final int theme;
	private final ItemStack modelStack;

	private TotemSkin(String id, String name, int theme) {
		this.id = id; this.name = name; this.theme = theme;
		this.modelStack = Skin.stackFor("totem/" + id);
	}

	public ItemStack modelStack() { return modelStack; }

	private static void add(String id, String name, int theme) { ALL.add(new TotemSkin(id, name, theme)); }

	static {
		add("classic", "Classic", 0xF2C230);
		add("ruby", "Ruby", 0xD4202A);
		add("sapphire", "Sapphire", 0x2F6FE0);
		add("emerald", "Emerald", 0x22B25A);
		add("amethyst", "Amethyst", 0x8A4FE0);
		add("rose", "Rose", 0xF070A8);
		add("ice", "Ice", 0x8FD8F5);
		add("obsidian", "Obsidian", 0x2B2838);
		add("snow", "Snow", 0xEEF4FB);
		add("lava", "Lava", 0xFF6A1A);
		add("ocean", "Ocean", 0x1E8FA8);
		add("sunset", "Sunset", 0xFF8A3A);
		add("mint", "Mint", 0x6FE0B0);
		add("candy", "Candy", 0xFF5A8A);
		add("midnight", "Midnight", 0x2A3A8A);
		add("copper", "Copper", 0xC46A3A);
		add("silver", "Silver", 0xB8C0CF);
		add("bronze", "Bronze", 0xA8742A);
		add("lime", "Lime", 0xA6E02A);
		add("orchid", "Orchid", 0xC060D0);
		add("crimson", "Crimson", 0x9A1028);
		add("teal", "Teal", 0x1FB0B0);
		add("peach", "Peach", 0xFFB48A);
		add("shadow", "Shadow", 0x4A4A5A);
		add("sky", "Sky", 0x5AB8FF);
		add("sunflower", "Sunflower", 0xF8E040);
	}

	public static TotemSkin byId(String id) {
		for (TotemSkin s : ALL) if (s.id.equals(id)) return s;
		return ALL.get(0);
	}

	public static TotemSkin current() { return byId(XmasConfig.get().totemSkin); }
}
