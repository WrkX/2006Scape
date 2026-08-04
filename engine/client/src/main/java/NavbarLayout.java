/**
 * Logical navbar layout at the reference 765x25 presentation.
 */
final class NavbarLayout {

	static final int WIDTH = 765;
	static final int HEIGHT = 25;

	final UiBounds bar;
	final UiBounds company;
	final UiBounds mainMenu;
	final UiBounds worldSelect;
	final UiBounds worldmap;
	final UiBounds manual;
	final UiBounds rules;

	private NavbarLayout() {
		bar = new UiBounds(0, 0, WIDTH, HEIGHT);
		company = new UiBounds(5, 0, 115, HEIGHT);
		mainMenu = new UiBounds(126, 0, 95, HEIGHT);
		worldSelect = new UiBounds(250, 0, 95, HEIGHT);
		worldmap = new UiBounds(387, 0, 95, HEIGHT);
		manual = new UiBounds(520, 0, 70, HEIGHT);
		rules = new UiBounds(636, 0, 120, HEIGHT);
	}

	static NavbarLayout logical() {
		return new NavbarLayout();
	}

	enum Item {
		NONE,
		COMPANY,
		MAIN_MENU,
		WORLD_SELECT,
		WORLDMAP,
		MANUAL,
		RULES
	}

	static Item hitTest(NavbarLayout layout, int logicalX, int logicalY) {
		if (!layout.bar.contains(logicalX, logicalY)) {
			return Item.NONE;
		}
		if (layout.company.contains(logicalX, logicalY)) {
			return Item.COMPANY;
		}
		if (layout.mainMenu.contains(logicalX, logicalY)) {
			return Item.MAIN_MENU;
		}
		if (layout.worldSelect.contains(logicalX, logicalY)) {
			return Item.WORLD_SELECT;
		}
		if (layout.worldmap.contains(logicalX, logicalY)) {
			return Item.WORLDMAP;
		}
		if (layout.manual.contains(logicalX, logicalY)) {
			return Item.MANUAL;
		}
		if (layout.rules.contains(logicalX, logicalY)) {
			return Item.RULES;
		}
		return Item.NONE;
	}

	UiBounds[] debugRegions() {
		return new UiBounds[] {
				bar,
				company,
				mainMenu,
				worldSelect,
				worldmap,
				manual,
				rules
		};
	}
}
