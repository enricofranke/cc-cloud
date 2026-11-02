// fengari (Lua 5.3 in JavaScript, MIT) ships no types; the mock's Lua runner (dev/code-lua.ts) uses it untyped.
declare module "fengari" {
  const fengari: any;
  export default fengari;
}
