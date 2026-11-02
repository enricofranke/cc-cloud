// Stands in for the Node modules fengari (the mock's Lua, dev/code-lua.ts) requires on its Node-only paths. The
// worker has no `process`, so fengari never takes them; without this they would bundle tmp and readline-sync.
export default {};
