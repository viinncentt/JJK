package at.vl.jJK.guis.admin;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public class AdminMenu {
	private final JJK jjk;

	// Admin Menu GUI
	private final String adminMenuTitle;

	public AdminMenu(JJK jjk) {
		this.jjk = jjk;

		adminMenuTitle = "<gradient:#4BAEB7:#700F52>JJK Admin Menu</gradient>";
	}

	public void open(Player player) {
		Window window = Window.builder()
				.setTitle(MiniMessage.miniMessage().deserialize(adminMenuTitle))
				.setUpperGui(createGui(player))
				.setViewer(player)
				.build();

		window.open();
	}

	private Gui createGui(Player player) {
		Item techniqueMenuItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.ALLIUM).setName("Technique Menu"))
				.addClickHandler(click -> {
					CursedTechniqueMenu ctm = new CursedTechniqueMenu(jjk);
					ctm.open(player);
				})
				.build();

		Item unlockMenuItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.EMERALD).setName("Unlock Abilities"))
				.addClickHandler(click -> new AdminUnlockMenu(jjk).open(player, player))
				.build();

		return Gui.builder()
				.setStructure("x x x j i x x x x")
				.addIngredient('i', techniqueMenuItem)
				.addIngredient('j', unlockMenuItem)
				.build();
	}
}
