package at.vl.jJK.guis;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.shikigami.ShikigamiManager.ShikigamiInfo;
import at.vl.jJK.cursedtechniques.shikigami.ShikigamiType;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public class ShikigamiMenu {
	private final JJK jjk;

	// Shikigami Menu GUI
	private final String shikigamiMenuTitle;

	public ShikigamiMenu(JJK jjk) {
		this.jjk = jjk;

		shikigamiMenuTitle = "<gradient:#4BAEB7:#700F52>Shikigami Menu</gradient>";
	}

	public void open(Player player) {
		Window window = Window.builder()
				.setTitle(MiniMessage.miniMessage().deserialize(shikigamiMenuTitle))
				.setUpperGui(createGui(player))
				.setViewer(player)
				.build();

		window.open();
	}

	private Gui createGui(Player player) {
		List<ShikigamiInfo> shikigami = jjk.getShikigamiManager().getStoredShikigami(player);

		if (shikigami.isEmpty()) {
			player.sendMessage(MiniMessage.miniMessage()
					.deserialize(jjk.getLogo() + " <#F61845>You have no shikigami"));
		}

		Item grayGlass = Item.builder()
				.setItemProvider(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(""))
				.build();

		int rows = Math.max(1, Math.min((int) Math.ceil((shikigami.size() + 1) / 9.0), 6));
		Gui gui = Gui.empty(9, rows);

		for (int i = 0; i < shikigami.size(); i++) {
			gui.setItem(i, createShikigamiItem(shikigami.get(i)));
		}

		int lastSlot = gui.getSize() - 1;
		for (int slot = shikigami.size(); slot < lastSlot; slot++) {
			gui.setItem(slot, grayGlass);
		}

		gui.setItem(lastSlot, createBackItem(player));

		return gui;
	}

	private Item createShikigamiItem(ShikigamiInfo shikigami) {
		ShikigamiType type = shikigami.type();

		Material material;
		try {
			material = Material.valueOf(type.getEntityType().name() + "_SPAWN_EGG");
		} catch (IllegalArgumentException e) {
			material = Material.PAPER;
		}

		int currentHealth = (int) Math.round(shikigami.health());
		int maxHealth = (int) Math.round(shikigami.maxHealth());

		return Item.builder()
				.setItemProvider(new ItemBuilder(material)
						.setName(MiniMessage.miniMessage().deserialize("<white>" + formatName(type)))
						.setLore(List.of(
								MiniMessage.miniMessage().deserialize(
										"<gray>Health: <white>" + currentHealth + "/" + maxHealth),
								MiniMessage.miniMessage().deserialize(
										"<gray>Cursed Energy Usage: <white>" + formatNumber(type.getCursedEnergyDrainAmount())),
								MiniMessage.miniMessage().deserialize(
										"<gray>Damage: <white>" + formatNumber(type.getDamage())))))
				.build();
	}

	private Item createBackItem(Player player) {
		return Item.builder()
				.setItemProvider(new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
						.setName(MiniMessage.miniMessage().deserialize("<red>Back")))
				.addClickHandler(click -> {
					JJKMenu jjkMenu = new JJKMenu(jjk);
					jjkMenu.open(player);
				})
				.build();
	}

	private String formatName(ShikigamiType type) {
		String[] words = type.name().split("_");
		StringBuilder builder = new StringBuilder();

		for (String word : words) {
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(word.charAt(0)).append(word.substring(1).toLowerCase());
		}

		return builder.toString();
	}

	private String formatNumber(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}
}
