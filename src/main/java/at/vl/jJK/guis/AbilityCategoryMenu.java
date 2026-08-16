package at.vl.jJK.guis;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.BindableCategory;
import at.vl.jJK.guis.bind.BindAbilityMenu;
import at.vl.jJK.guis.bind.BindMeleeMenu;
import at.vl.jJK.guis.bind.BindSpecialMoveMenu;
import at.vl.jJK.guis.bind.BindWeaponeeringMenu;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public class AbilityCategoryMenu {
	private final JJK jjk;

	// Ability Category Menu GUI
	private final String categoryMenuTitle;

	public AbilityCategoryMenu(JJK jjk) {
		this.jjk = jjk;

		categoryMenuTitle = "<gradient:#4BAEB7:#700F52>Choose Category</gradient>";
	}

	public void open(Player player, int slot) {
		Window window = Window.builder()
				.setTitle(MiniMessage.miniMessage().deserialize(categoryMenuTitle))
				.setUpperGui(createGui(player, slot))
				.setViewer(player)
				.build();

		window.open();
	}

	private Gui createGui(Player player, int slot) {
		Item grayGlass = Item.builder()
				.setItemProvider(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(""))
				.build();

		Gui gui = Gui.empty(9, 2);
		for (int i = 0; i < gui.getSize(); i++) {
			gui.setItem(i, grayGlass);
		}

		boolean specialUnlocked = jjk.getSpecialMoveManager().hasAnyUnlocked(player);

		if (specialUnlocked) {
			gui.setItem(1, createCategoryItem(player, slot, BindableCategory.CURSED_TECHNIQUE));
			gui.setItem(3, createCategoryItem(player, slot, BindableCategory.MELEE));
			gui.setItem(5, createCategoryItem(player, slot, BindableCategory.WEAPONEERING));
			gui.setItem(7, createCategoryItem(player, slot, BindableCategory.SPECIAL_MOVE));
		} else {
			gui.setItem(2, createCategoryItem(player, slot, BindableCategory.CURSED_TECHNIQUE));
			gui.setItem(4, createCategoryItem(player, slot, BindableCategory.MELEE));
			gui.setItem(6, createCategoryItem(player, slot, BindableCategory.WEAPONEERING));
		}

		gui.setItem(16, createNoneItem(player, slot));
		gui.setItem(17, createBackItem(player));

		return gui;
	}

	private Item createCategoryItem(Player player, int slot, BindableCategory category) {
		Material material = switch (category) {
			case CURSED_TECHNIQUE -> Material.NETHER_STAR;
			case MELEE -> Material.IRON_SWORD;
			case WEAPONEERING -> Material.BOW;
			case SPECIAL_MOVE -> Material.BEACON;
		};
		String name = switch (category) {
			case CURSED_TECHNIQUE -> "Cursed Techniques";
			case MELEE -> "Melee";
			case WEAPONEERING -> "Weaponeering";
			case SPECIAL_MOVE -> "Special Moves";
		};

		return Item.builder()
				.setItemProvider(new ItemBuilder(material).setName(name))
				.addClickHandler(click -> {
					switch (category) {
						case CURSED_TECHNIQUE -> new BindAbilityMenu(jjk).open(player, slot);
						case MELEE -> new BindMeleeMenu(jjk).open(player, slot);
						case WEAPONEERING -> new BindWeaponeeringMenu(jjk).open(player, slot);
						case SPECIAL_MOVE -> new BindSpecialMoveMenu(jjk).open(player, slot);
					}
				})
				.build();
	}

	private Item createNoneItem(Player player, int slot) {
		return Item.builder()
				.setItemProvider(new ItemBuilder(Material.RED_DYE)
						.setName("Bind Nothing"))
				.addClickHandler(click -> {
					jjk.getBindManager().bindNothing(player, slot);
					AbilityMenu abilityMenu = new AbilityMenu(jjk);
					abilityMenu.open(player);
				})
				.build();
	}

	private Item createBackItem(Player player) {
		return Item.builder()
				.setItemProvider(new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
						.setName(MiniMessage.miniMessage().deserialize("<red>Back")))
				.addClickHandler(click -> {
					AbilityMenu abilityMenu = new AbilityMenu(jjk);
					abilityMenu.open(player);
				})
				.build();
	}
}
