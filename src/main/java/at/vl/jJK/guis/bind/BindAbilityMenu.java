package at.vl.jJK.guis.bind;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.technique.AbilityType;
import at.vl.jJK.cursedtechniques.technique.CursedTechniqueType;
import at.vl.jJK.guis.AbilityCategoryMenu;
import at.vl.jJK.guis.AbilityMenu;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public class BindAbilityMenu {
	private final JJK jjk;

	// Bind ability GUI
	private final String bindAbilityTitle;

	public BindAbilityMenu(JJK jjk) {
		this.jjk = jjk;

		bindAbilityTitle = "<gradient:#4BAEB7:#700F52>Bind Ability</gradient>";
	}

	public void open(Player player, int slot) {
		Window window = Window.builder()
				.setTitle(MiniMessage.miniMessage().deserialize(bindAbilityTitle))
				.setUpperGui(createBAM(player, slot))
				.setViewer(player)
				.build();

		window.open();
	}

	private Gui createBAM(Player player, int slot) {
		CursedTechniqueType playerTechnique = jjk.getCursedTechniqueManager().getPlayerCursedTechnique(player);
		List<AbilityType> abilities = Arrays.stream(AbilityType.values())
				.filter(ability -> ability.getCursedTechniques() == playerTechnique)
				.toList();

		Item grayGlass = Item.builder()
				.setItemProvider(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(""))
				.build();

		if (abilities.isEmpty()) {
			player.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo() + " <#F61845>You have no abilities"));
		}

		int rows = Math.max(1, Math.min((int) Math.ceil((abilities.size() + 1) / 9.0), 6));
		Gui gui = Gui.empty(9, rows);

		for (int i = 0; i < abilities.size(); i++) {
			if (jjk.getAbilityManager().hasAbility(player, abilities.get(i))) {
				gui.setItem(i, createAbilityItem(player, slot, abilities.get(i)));
			} else {
				gui.setItem(i, grayGlass);
			}
		}

		int lastSlot = gui.getSize() - 1;

		for (int s = abilities.size(); s < lastSlot; s++) {
			gui.setItem(s, grayGlass);
		}

		gui.setItem(lastSlot, createBackItem(player, slot));

		return gui;
	}

	private Item createAbilityItem(Player player, int slot, AbilityType ability) {
		return Item.builder()
				.setItemProvider(new ItemBuilder(ability.getMaterial())
						.setName(ability.getDisplayName()))
				.addClickHandler(click -> {
					jjk.getBindManager().bind(player, slot, ability);
					AbilityMenu abilityMenu = new AbilityMenu(jjk);
					abilityMenu.open(player);
				})
				.build();
	}

	private Item createBackItem(Player player, int slot) {
		return Item.builder()
				.setItemProvider(new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
						.setName(MiniMessage.miniMessage().deserialize("<red>Back")))
				.addClickHandler(click -> new AbilityCategoryMenu(jjk).open(player, slot))
				.build();
	}

}
