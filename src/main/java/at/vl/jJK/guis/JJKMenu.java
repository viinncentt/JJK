package at.vl.jJK.guis;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public class JJKMenu {
	private final JJK jjk;

	// JJK Menu GUI
	private final String jjkMenuTitle;

	public JJKMenu(JJK jjk) {
		this.jjk = jjk;

		jjkMenuTitle = "<gradient:#4BAEB7:#700F52>JJK Menu</gradient>";
	}

	public void open(Player player) {
		Window window = Window.builder()
				.setTitle(MiniMessage.miniMessage().deserialize(jjkMenuTitle))
				.setUpperGui(createGui(player))
				.setViewer(player)
				.build();

		window.open();
	}

	private Gui createGui(Player player) {
		Item abilitiesMenuItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.LAPIS_LAZULI).setName("Ability Menu"))
				.addClickHandler(click -> {
					AbilityMenu am = new AbilityMenu(jjk);
					am.open(player);
				})
				.build();

		Item shikigamiMenuItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.WOLF_SPAWN_EGG).setName("Shikigami Menu"))
				.addClickHandler(click -> {
					ShikigamiMenu sm = new ShikigamiMenu(jjk);
					sm.open(player);
				})
				.build();

		return Gui.builder()
				.setStructure("x x x j i x x x x")
				.addIngredient('i', abilitiesMenuItem)
				.addIngredient('j', shikigamiMenuItem)
				.build();
	}
}
