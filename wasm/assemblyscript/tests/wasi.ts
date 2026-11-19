// Imports a WASI function: refused when it is saved and when it starts.
@external("wasi_snapshot_preview1", "fd_write")
declare function fdWrite(fd: i32, iovs: i32, n: i32, written: i32): i32;

export function main(): void {
  fdWrite(1, 0, 0, 0);
}
