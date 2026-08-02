/**
 * Scripted shop definitions of Dragon Island.
 *
 * Phase 5 WP4 migration: the former nested author-side {@code Shop} objects
 * were inert. The island general store is now a canonical schema-v1
 * {@code defineShop} with definition-backed numeric item ids, declared
 * stock and prices, stock-capped buying, and a bounded restock interval.
 * The area NPC spawn {@code villager-shopkeeper} opens it through its exact
 * allocation route.
 *
 * @module areas/dragon_island/shops
 */

import type { ShopDefinition } from "../../core/shop.js";

const islandGeneralStore: ShopDefinition = {
  id: "dragon_island_general",
  name: "Island Supplies",
  items: [
    { itemId: 379, amount: 10, price: 150 }, // Lobster
    { itemId: 372, amount: 10, price: 200 }, // Swordfish
    { itemId: 3144, amount: 5, price: 400 }, // Cooked karambwan
    { itemId: 2434, amount: 3, price: 2500 }, // Prayer potion (4)
    { itemId: 1540, amount: 5, price: 500 }, // Anti-dragon shield
    { itemId: 590, amount: 10, price: 1 }, // Tinderbox
    { itemId: 954, amount: 10, price: 15 }, // Rope
  ],
  buys: true,
  restockTicks: 250,
};

defineShop(islandGeneralStore);
