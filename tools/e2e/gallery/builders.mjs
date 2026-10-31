// Machines that need more than one block. A line in machines.tsv with build=<name> calls the builder after its block is
// placed: ctx = { cmd, x, y, z, Y, FLOOR, cell, sleep, mainframe, rec }. rec.uplink = [x, y, z, facing] overrides the uplink.
const hollow3 = async ({ cmd, x, y, z }, c) => cmd(`fill ${x - 1} ${y} ${z - 1} ${x + 1} ${y + 2} ${z + 1} ${c.block} outline`);
export const BUILDERS = {
  /** A 3x3x3 shell (Railcraft tank and coke oven). */
  hollow3,
  /** A controller with a drawer that holds items, which the card then counts. */
  fs_controller: async ({ cmd, x, y, z }) => {
    await cmd(`setblock ${x + 1} ${y} ${z} functionalstorage:oak_1`);
    await cmd(`cccloud dev item ${x + 1} ${y} ${z} minecraft:bread 16`, { allowFail: true });
  },
  sophisticated_controller: async ({ cmd, x, y, z }) => {
    await cmd(`setblock ${x + 1} ${y} ${z} sophisticatedstorage:chest`);
    await cmd(`cccloud dev item ${x + 1} ${y} ${z} minecraft:bread 16`, { allowFail: true });
  },
  /** Creative energy and a drive with a cell: a real ME network around the bridge. */
  me_bridge: async ({ cmd, x, y, z }) => {
    await cmd(`setblock ${x + 1} ${y} ${z} ae2:creative_energy_cell`);
    await cmd(`setblock ${x - 1} ${y} ${z} ae2:drive`);
    await cmd(`cccloud dev item ${x - 1} ${y} ${z} ae2:item_storage_cell_1k 1`, { allowFail: true });
  },
  /** Creative controller and a storage block: a real RS network around the bridge. */
  rs_bridge: async ({ cmd, x, y, z }) => {
    await cmd(`setblock ${x + 1} ${y} ${z} refinedstorage:creative_controller`);
    await cmd(`setblock ${x - 1} ${y} ${z} refinedstorage:1k_storage_block`);
  },
};
