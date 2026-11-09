package dev.xmas.overlay;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** One butterfly-knife skin: name, picture id, menu colour and the item stacks that carry its models. */
public final class Skin {
	public static final List<Skin> ALL = new ArrayList<>();

	public final String id, name, blurb, category;   // category: "christmas" or "classic"
	public final int theme;               // menu colour (RGB) when this skin is equipped
	private final ItemStack modelStack;
	private final ItemStack[] parts = new ItemStack[3];

	private Skin(String id, String name, String blurb, int theme, String category) {
		this.id = id; this.name = name; this.blurb = blurb; this.theme = theme; this.category = category;
		this.modelStack = stackFor("knife/" + id);
		this.parts[0] = stackFor("part/" + id + "_blade");
		this.parts[1] = stackFor("part/" + id + "_a");
		this.parts[2] = stackFor("part/" + id + "_b");
	}

	static ItemStack stackFor(String path) {
		ItemStack s = new ItemStack(Items.TOTEM_OF_UNDYING);
		s.set(DataComponentTypes.ITEM_MODEL, Identifier.of("xmasoverlay", path));
		return s;
	}

	public ItemStack modelStack() { return modelStack; }
	/** 0 = blade, 1 = handle A (front), 2 = handle B (back) */
	public ItemStack part(int i) { return parts[i]; }

	private static String cat = "christmas";
	private static void add(String id, String name, String blurb, int theme) { ALL.add(new Skin(id, name, blurb, theme, cat)); }

	static {
		add("candy_cane", "Candy Cane", "Red and white stripes like a candy cane", 0xE8202A);
		add("frostbite", "Frostbite", "Ice crystals and cold snow", 0x7FD0FF);
		add("evergreen", "Evergreen", "Pine green with red berries", 0x1FA84F);
		add("golden_bells", "Golden Bells", "Shiny festive gold", 0xFFCF3A);
		add("galaxy", "Galaxy", "Purple and pink stardust", 0x8A5CFF);
		add("gingerbread", "Gingerbread", "Warm cookie brown with icing", 0xC9843A);
		add("snowflake", "Snowflake", "Pure white with snowflakes", 0xEAF4FF);
		add("ruby_ornament", "Ruby Ornament", "Deep red ruby and gold", 0xE0141E);
		add("aurora", "Aurora", "Northern lights, green to purple", 0x38F2A0);
		add("holly_berry", "Holly & Berry", "Green leaves and red berries", 0x2FBF5F);
		add("silver_tinsel", "Silver Tinsel", "Glittering silver tinsel", 0xDCE0E8);
		add("hot_cocoa", "Hot Cocoa", "Chocolate brown and marshmallow", 0x9A6540);
		add("reindeer", "Reindeer", "Soft fur with a red nose", 0xD0A060);
		add("fireplace", "Fireplace", "Dark iron with glowing embers", 0xFF7A1A);
		add("midnight_star", "Midnight Star", "Gold stars on a midnight sky", 0x4C6FE0);
		cat = "classic";
		add("damascus", "Damascus", "Drop-point blade of folded steel, rosewood grip", 0xD9A441);
		add("carbon", "Carbon Tanto", "Tanto blade, carbon fibre handles", 0xE8202A);
		add("neon", "Neon Spear", "Spear-point blade, glowing skeleton handles", 0x19F0FF);
		add("obsidian", "Obsidian", "Black volcanic glass, hex handles", 0xB06CFF);
		add("dragon", "Dragon Fang", "Hooked hawkbill blade, scaled grip", 0xFFB347);
		add("samurai", "Samurai", "Curved cutlass blade with a hamon line, cord wrap", 0xD9B44A);
		add("toxic", "Toxic Saw", "Serrated blade dripping green slime", 0xA6E02A);
		add("rustic", "Rustic Cleaver", "Wide cleaver blade, worn and rusty", 0xC9A060);
		add("ocean", "Ocean Wave", "Recurve blade with rolling waves", 0x4AB8E8);
		add("magma", "Magma", "Kukri blade with glowing lava cracks", 0xFF5A10);
		add("sakura", "Sakura", "Sheepsfoot blade with cherry blossoms", 0xF08AAE);
		add("rosewood", "Rosewood", "Bowie blade, polished rosewood handles", 0xC9A050);
		add("cyber", "Cyberpunk", "Trailing-point blade with glowing circuits", 0xFF2FD0);
		add("bone", "Bone Talon", "Curved talon carved from bone", 0xE8DFC8);
		add("royal", "Royal Dagger", "Double-edged dagger, gold and purple filigree", 0xFFE08A);
		add("tiger", "Tiger", "Slim stiletto with tiger stripes", 0xE8780A);
		add("woodland", "Woodland", "Leaf blade in forest camouflage", 0x8A9A4A);
		add("chrome", "Mirror Chrome", "Mirror-polished fang blade", 0x9EC8FF);
		add("blood_moon", "Blood Moon", "Dark crimson scythe blade", 0xD01030);
		add("sandstorm", "Sandstorm", "Harpoon blade, desert sand and cord wrap", 0xE8D098);
	}

	public static Skin byId(String id) {
		for (Skin s : ALL) if (s.id.equals(id)) return s;
		return ALL.get(0);
	}

	public static Skin current() { return byId(XmasConfig.get().skin); }

	private static final Identifier DEFAULT_TOTEM_MODEL = Identifier.ofVanilla("totem_of_undying");

	/** Used by the item model mixin: gives the totem the model of the chosen knife skin or totem skin. */
	public static ItemStack swap(ItemStack stack) {
		try {
			if (!Safe.ok("skins") || !stack.isOf(Items.TOTEM_OF_UNDYING)) return stack;
			Identifier cur = stack.get(DataComponentTypes.ITEM_MODEL);
			if (cur == null || !cur.equals(DEFAULT_TOTEM_MODEL)) return stack;
			XmasConfig cfg = XmasConfig.get();
			return cfg.customKnife ? current().modelStack() : TotemSkin.byId(cfg.totemSkin).modelStack();
		} catch (Throwable t) { Safe.fail("skins", t); }
		return stack;
	}
}
