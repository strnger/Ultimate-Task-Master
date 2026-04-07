package com.ultimatetaskmaster.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemID;

/**
 * Static mapping of common item names (from strategy.json) to RuneLite {@link ItemID} constants.
 *
 * Provides a reliable fallback for item ID resolution when the bank is not open
 * or when {@code UtmBankTab.findItemIdByName} cannot search live bank contents.
 *
 * All lookups are case-insensitive. Items with tier variants (e.g., axes) expose
 * the lowest tier as the primary ID and all tiers via {@link #getAlternateIds}.
 *
 * Categories that cannot map to a single item (e.g., "combat gear", "warm clothing")
 * return {@code -1} from {@link #getItemId}.
 */
public final class ItemIdMapping
{
	/** Primary item name → ItemID mapping (lowercase keys). */
	private static final Map<String, Integer> PRIMARY_IDS;

	/** Item name → list of alternate ItemIDs for tiered/variant items (lowercase keys). */
	private static final Map<String, List<Integer>> ALTERNATE_IDS;

	static
	{
		Map<String, Integer> ids = new HashMap<>();
		Map<String, List<Integer>> alts = new HashMap<>();

		// ── Tools ──────────────────────────────────────────────────────
		ids.put("axe", ItemID.BRONZE_AXE);
		ids.put("any axe", ItemID.BRONZE_AXE);
		ids.put("bronze axe", ItemID.BRONZE_AXE);
		ids.put("iron axe", ItemID.IRON_AXE);
		ids.put("steel axe", ItemID.STEEL_AXE);
		ids.put("mithril axe", ItemID.MITHRIL_AXE);
		ids.put("adamant axe", ItemID.ADAMANT_AXE);
		ids.put("rune axe", ItemID.RUNE_AXE);
		ids.put("dragon axe", ItemID.DRAGON_AXE);
		alts.put("axe", Collections.unmodifiableList(Arrays.asList(
			ItemID.BRONZE_AXE,
			ItemID.IRON_AXE,
			ItemID.STEEL_AXE,
			ItemID.BLACK_AXE,
			ItemID.MITHRIL_AXE,
			ItemID.ADAMANT_AXE,
			ItemID.RUNE_AXE,
			ItemID.DRAGON_AXE
		)));
		alts.put("any axe", alts.get("axe"));

		// ── Pickaxes ──────────────────────────────────────────────────
		ids.put("pickaxe", ItemID.BRONZE_PICKAXE);
		ids.put("any pickaxe", ItemID.BRONZE_PICKAXE);
		ids.put("bronze pickaxe", ItemID.BRONZE_PICKAXE);
		ids.put("iron pickaxe", ItemID.IRON_PICKAXE);
		ids.put("steel pickaxe", ItemID.STEEL_PICKAXE);
		ids.put("mithril pickaxe", ItemID.MITHRIL_PICKAXE);
		ids.put("adamant pickaxe", ItemID.ADAMANT_PICKAXE);
		ids.put("rune pickaxe", ItemID.RUNE_PICKAXE);
		ids.put("dragon pickaxe", ItemID.DRAGON_PICKAXE);
		alts.put("pickaxe", Collections.unmodifiableList(Arrays.asList(
			ItemID.BRONZE_PICKAXE, ItemID.IRON_PICKAXE, ItemID.STEEL_PICKAXE,
			ItemID.MITHRIL_PICKAXE, ItemID.ADAMANT_PICKAXE, ItemID.RUNE_PICKAXE,
			ItemID.DRAGON_PICKAXE
		)));
		alts.put("any pickaxe", alts.get("pickaxe"));

		ids.put("tinderbox", ItemID.TINDERBOX);
		ids.put("knife", ItemID.KNIFE);
		ids.put("hammer", ItemID.HAMMER);
		ids.put("chisel", ItemID.CHISEL);
		ids.put("saw", ItemID.SAW);
		ids.put("needle", ItemID.NEEDLE);
		ids.put("spade", ItemID.SPADE);
		ids.put("rope", ItemID.ROPE);
		ids.put("bucket", ItemID.BUCKET);
		ids.put("bucket of water", ItemID.BUCKET_OF_WATER);
		ids.put("seed dibber", ItemID.SEED_DIBBER);
		ids.put("rake", ItemID.RAKE);

		// ── Ores ───────────────────────────────────────────────────────
		ids.put("iron ore", ItemID.IRON_ORE);
		ids.put("copper ore", ItemID.COPPER_ORE);
		ids.put("tin ore", ItemID.TIN_ORE);
		ids.put("coal", ItemID.COAL);
		ids.put("gold ore", ItemID.GOLD_ORE);
		ids.put("silver ore", ItemID.SILVER_ORE);
		ids.put("mithril ore", ItemID.MITHRIL_ORE);
		ids.put("adamantite ore", ItemID.ADAMANTITE_ORE);
		ids.put("runite ore", ItemID.RUNITE_ORE);

		// ── Bars ───────────────────────────────────────────────────────
		ids.put("iron bar", ItemID.IRON_BAR);
		ids.put("steel bar", ItemID.STEEL_BAR);
		ids.put("gold bar", ItemID.GOLD_BAR);
		ids.put("silver bar", ItemID.SILVER_BAR);
		ids.put("mithril bar", ItemID.MITHRIL_BAR);
		ids.put("bronze bar", ItemID.BRONZE_BAR);

		// ── Runes ──────────────────────────────────────────────────────
		ids.put("fire rune", ItemID.FIRE_RUNE);
		ids.put("water rune", ItemID.WATER_RUNE);
		ids.put("air rune", ItemID.AIR_RUNE);
		ids.put("earth rune", ItemID.EARTH_RUNE);
		ids.put("mind rune", ItemID.MIND_RUNE);
		ids.put("body rune", ItemID.BODY_RUNE);
		ids.put("cosmic rune", ItemID.COSMIC_RUNE);
		ids.put("nature rune", ItemID.NATURE_RUNE);
		ids.put("law rune", ItemID.LAW_RUNE);
		ids.put("death rune", ItemID.DEATH_RUNE);
		ids.put("blood rune", ItemID.BLOOD_RUNE);
		ids.put("soul rune", ItemID.SOUL_RUNE);
		ids.put("astral rune", ItemID.ASTRAL_RUNE);
		ids.put("wrath rune", ItemID.WRATH_RUNE);
		ids.put("rune essence", ItemID.RUNE_ESSENCE);
		ids.put("pure essence", ItemID.PURE_ESSENCE);

		// ── Fishing ────────────────────────────────────────────────────
		ids.put("small fishing net", ItemID.SMALL_FISHING_NET);
		ids.put("fishing rod", ItemID.FISHING_ROD);
		ids.put("fly fishing rod", ItemID.FLY_FISHING_ROD);
		ids.put("harpoon", ItemID.HARPOON);
		ids.put("lobster pot", ItemID.LOBSTER_POT);
		ids.put("feather", ItemID.FEATHER);
		ids.put("fishing bait", ItemID.FISHING_BAIT);
		ids.put("raw shrimps", ItemID.RAW_SHRIMPS);
		ids.put("raw trout", ItemID.RAW_TROUT);

		// ── Combat (arrows) ────────────────────────────────────────────
		ids.put("bronze arrow", ItemID.BRONZE_ARROW);
		ids.put("iron arrow", ItemID.IRON_ARROW);
		ids.put("steel arrow", ItemID.STEEL_ARROW);
		ids.put("mithril arrow", ItemID.MITHRIL_ARROW);

		// ── Food ───────────────────────────────────────────────────────
		ids.put("bread", ItemID.BREAD);
		ids.put("cake", ItemID.CAKE);
		ids.put("pie", ItemID.MEAT_PIE);
		ids.put("jug of wine", ItemID.JUG_OF_WINE);
		ids.put("cooked meat", ItemID.COOKED_MEAT);
		ids.put("lobster", ItemID.LOBSTER);
		ids.put("swordfish", ItemID.SWORDFISH);
		ids.put("monkfish", ItemID.MONKFISH);
		ids.put("shark", ItemID.SHARK);

		// ── Logs ───────────────────────────────────────────────────────
		ids.put("logs", ItemID.LOGS);
		ids.put("oak logs", ItemID.OAK_LOGS);
		ids.put("willow logs", ItemID.WILLOW_LOGS);
		ids.put("maple logs", ItemID.MAPLE_LOGS);
		ids.put("yew logs", ItemID.YEW_LOGS);
		ids.put("magic logs", ItemID.MAGIC_LOGS);
		ids.put("redwood logs", ItemID.REDWOOD_LOGS);
		ids.put("teak logs", ItemID.TEAK_LOGS);

		// ── Miscellaneous ──────────────────────────────────────────────
		ids.put("coins", ItemID.COINS_995);
		ids.put("bones", ItemID.BONES);
		ids.put("thread", ItemID.THREAD);
		ids.put("leather", ItemID.LEATHER);
		ids.put("soft clay", ItemID.SOFT_CLAY);
		ids.put("compost", ItemID.COMPOST);
		ids.put("supercompost", ItemID.SUPERCOMPOST);
		ids.put("watering can", ItemID.WATERING_CAN);

		// ── Special groups (unmappable or default) ─────────────────────
		// "food" is a generic category; default to lobster as a reasonable icon
		ids.put("food", ItemID.LOBSTER);
		// "combat gear" and "warm clothing" are too vague to map
		// They are intentionally absent so getItemId() returns -1.

		PRIMARY_IDS = Collections.unmodifiableMap(ids);
		ALTERNATE_IDS = Collections.unmodifiableMap(alts);
	}

	private ItemIdMapping()
	{
		// utility class — no instances
	}

	/**
	 * Look up the primary {@link ItemID} for an item name.
	 *
	 * @param itemName the item name to look up (case-insensitive)
	 * @return the RuneLite ItemID constant, or {@code -1} if no mapping exists
	 */
	public static int getItemId(String itemName)
	{
		if (itemName == null)
		{
			return -1;
		}
		Integer id = PRIMARY_IDS.get(itemName.toLowerCase().trim());
		return id != null ? id : -1;
	}

	/**
	 * Get alternate item IDs for items with tier/variant options.
	 * For example, "axe" returns IDs for bronze through dragon axes.
	 *
	 * @param itemName the item name to look up (case-insensitive)
	 * @return list of alternate ItemIDs, or an empty list if none exist
	 */
	public static List<Integer> getAlternateIds(String itemName)
	{
		if (itemName == null)
		{
			return Collections.emptyList();
		}
		List<Integer> ids = ALTERNATE_IDS.get(itemName.toLowerCase().trim());
		return ids != null ? ids : Collections.emptyList();
	}

	/**
	 * Check whether a static mapping exists for the given item name.
	 *
	 * @param itemName the item name to check (case-insensitive)
	 * @return {@code true} if a primary ID mapping exists
	 */
	public static boolean hasMapping(String itemName)
	{
		if (itemName == null)
		{
			return false;
		}
		return PRIMARY_IDS.containsKey(itemName.toLowerCase().trim());
	}

	/**
	 * Returns the total number of items with static mappings.
	 * Useful for diagnostics and logging.
	 *
	 * @return count of mapped item names
	 */
	public static int getMappingCount()
	{
		return PRIMARY_IDS.size();
	}
}
