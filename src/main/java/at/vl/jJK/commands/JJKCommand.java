package at.vl.jJK.commands;

import java.util.List;

import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;

public interface JJKCommand {
	LiteralCommandNode<CommandSourceStack> build();

	default List<String> aliases() {
		return List.of(); // empty by default
	}

	default String description() {
		return null; // null by default
	}
}
