onCommand("hello", (context) => {
  const { player } = context;
  const args = context.getArguments();
  const suffix = args.length > 0 ? ` Arguments: ${args.join(", ")}.` : "";
  player.message(
    `Hello, ${player.getUsername()}! Rights: ${context.getRights()}.${suffix}`,
  );
});

export {};
