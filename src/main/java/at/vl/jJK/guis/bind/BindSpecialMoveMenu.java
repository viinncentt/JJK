package at.vl.jJK.guis.bind;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.specialmove.SpecialMoveType;
import at.vl.jJK.guis.AbilityCategoryMenu;
import at.vl.jJK.guis.AbilityMenu;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public class BindSpecialMoveMenu {
	private final JJK jjk;

	// Bind Special Move GUI
	private final String bindSpecialMoveTitle;

	public BindSpecialMoveMenu(JJK jjk) {
		this.jjk = jjk;

		bindSpecialMoveTitle = "<gradient:#4BAEB7:#700F52>Bind Special Move</gradient>";
	}

	public void open(Player player, int slot) {
		Window window = Window.builder()
				.setTitle(MiniMessage.miniMessage().deserialize(bindSpecialMoveTitle))
				.setUpperGui(createBSM(player, slot))
				.setViewer(player)
				.build();

		window.open();
	}

	private Gui createBSM(Player player, int slot) {
		List<SpecialMoveType> moves = Arrays.asList(SpecialMoveType.values());

		Item grayGlass = Item.builder()
				.setItemProvider(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(""))
				.build();

		if (moves.isEmpty()) {
			player.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo() + " <#F61845>You have no special moves"));
		}

		int rows = Math.max(1, Math.min((int) Math.ceil((moves.size() + 1) / 9.0), 6));
		Gui gui = Gui.empty(9, rows);

		for (int i = 0; i < moves.size(); i++) {
			if (jjk.getSpecialMoveManager().hasMove(player, moves.get(i))) {
				gui.setItem(i, createMoveItem(player, slot, moves.get(i)));
			} else {
				gui.setItem(i, grayGlass);
			}
		}

		int lastSlot = gui.getSize() - 1;

		for (int s = moves.size(); s < lastSlot; s++) {
			gui.setItem(s, grayGlass);
		}

		gui.setItem(lastSlot, createBackItem(player, slot));

		return gui;
	}

	private Item createMoveItem(Player player, int slot, SpecialMoveType move) {
		return Item.builder()
				.setItemProvider(new ItemBuilder(move.getMaterial())
						.setName(move.getDisplayName()))
				.addClickHandler(click -> {
					jjk.getBindManager().bind(player, slot, move);
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
