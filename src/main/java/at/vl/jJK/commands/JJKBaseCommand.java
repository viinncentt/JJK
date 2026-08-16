package at.vl.jJK.commands;

import org.bukkit.entity.Player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.guis.JJKMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public class JJKBaseCommand implements JJKCommand {

	private final JJK jjk;

	public JJKBaseCommand(JJK jjk) {
		this.jjk = jjk;
	}

	@Override
	public LiteralCommandNode<CommandSourceStack> build() {
		return Commands.literal("jjk")

				// Start
				.then(startCommand("start"))

				// Cursed Technique
				.then(cursedTechniqueCommand("cursedtechnique"))

				// Menu
				.then(menuCommand("menu"))
				.then(menuCommand("m"))

				.build();
	}

	private LiteralCommandNode<CommandSourceStack> startCommand(String name) {
		return Commands.literal(name)
				.executes(ctx -> {
					Player player = (Player) ctx.getSource().getSender();

					player.sendMessage(MiniMessage.miniMessage()
							.deserialize(jjk.getLogo() + " <white>WIP</white>"));

					return Command.SINGLE_SUCCESS;
				})
				.build();
	}

	private LiteralCommandNode<CommandSourceStack> cursedTechniqueCommand(String name) {
		return Commands.literal(name)
				.then(Commands.argument("list", StringArgumentType.word())
						.executes(ctx -> {
							Player player = (Player) ctx.getSource().getSender();

							player.sendMessage(jjk.getCursedTechniqueManager().getPlayerCursedTechnique(player).getDisplayNameComponent());

							return Command.SINGLE_SUCCESS;
						})
				)
				.build();
	}

	private LiteralCommandNode<CommandSourceStack> menuCommand(String name) {
		return Commands.literal(name)
				.executes(ctx -> {
					Player player = (Player) ctx.getSource().getSender();

					player.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo() + "<#39EF4D> Successfully opened JJK menu"));
					JJKMenu jjkm = new JJKMenu(jjk);
					jjkm.open(player);

					return Command.SINGLE_SUCCESS;
				})

				.build();
	}
}
