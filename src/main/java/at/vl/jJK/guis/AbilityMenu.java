package at.vl.jJK.guis;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public class AbilityMenu {
	private final JJK jjk;

	// Ability Menu GUI
	private final String abilityMenuTitle;

	public AbilityMenu(JJK jjk) {
		this.jjk = jjk;

		abilityMenuTitle = "<gradient:#4BAEB7:#700F52>Ability Menu</gradient>";
	}

	public void open(Player player) {
		Item grayGlass = Item.builder()
				.setItemProvider(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(""))
				.build();


		Item back = Item.builder()
				.setItemProvider(new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
						.setName(MiniMessage.miniMessage().deserialize("<red>Back")))
				.addClickHandler(click -> {
					JJKMenu jjkMenu = new JJKMenu(jjk);
					jjkMenu.open(player);
				})
				.build();

		Gui am = Gui.builder()
				.setStructure("z z z z z z z z z",
						      "a b c d e f g h i",
							  "z z z z z z z z !")
				.addIngredient('z', grayGlass)
				.addIngredient('a', createSlotItem(player, 0))
				.addIngredient('b', createSlotItem(player, 1))
				.addIngredient('c', createSlotItem(player, 2))
				.addIngredient('d', createSlotItem(player, 3))
				.addIngredient('e', createSlotItem(player, 4))
				.addIngredient('f', createSlotItem(player, 5))
				.addIngredient('g', createSlotItem(player, 6))
				.addIngredient('h', createSlotItem(player, 7))
				.addIngredient('i', createSlotItem(player, 8))
				.addIngredient('!', back)
				.build();

		Window window = Window.builder()
				.setTitle(MiniMessage.miniMessage().deserialize(abilityMenuTitle))
				.setUpperGui(am)
				.setViewer(player)
				.build();

		window.open();
	}

	public Item createSlotItem(Player player, int slot) {
		if (jjk.getBindManager().getBoundAbility(player, slot) != null) {
			return Item.builder()
					.setItemProvider(new ItemBuilder(jjk.getBindManager().getBoundAbility(player, slot).getMaterial())
							.setName(MiniMessage.miniMessage().deserialize("Slot " + (slot + 1) + ": " + jjk.getBindManager().getBoundAbility(player, slot).getDisplayName()))
							.setAmount(slot + 1))
					.addClickHandler(click -> new AbilityCategoryMenu(jjk).open(player, slot))
					.build();
		} else {
			return Item.builder()
					.setItemProvider(new ItemBuilder(Material.WHITE_STAINED_GLASS_PANE)
							.setName("Slot " + (slot + 1))
							.setAmount(slot + 1))
					.addClickHandler(click -> new AbilityCategoryMenu(jjk).open(player, slot))
					.build();
		}
	}
}
