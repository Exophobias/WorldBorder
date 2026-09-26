# WorldBorder (Pure's Fork)

**Patriam fork: targets Paper 26.3 and Java 25.** The original Puremin0rez 1.19
release is preserved in the upstream `1.19` tag. This branch has not been tested
on older Minecraft versions.

The AoK 1.20 server used the upstream 1.19 release. This port retains its
per-world rectangular borders, `/wborder` (`/wb`) commands, and existing
`plugins/WorldBorder/config.yml` layout. For example, the proposed
`PatriamEotW` chunk-aligned outer edges `(4496, 8256)` and `(12864, 22512)`
can be entered with `/wb PatriamEotW setcorners 4496 8256 12864 22512`, then
`/wb wshape PatriamEotW rectangular`.

WorldBorder enforces its own border. It does not change Bukkit's native square
`World#getWorldBorder()` result, so other plugins that read that API need to
handle the rectangle separately.

When BlueMap's Paper build is installed, WorldBorder adds a red outline to every
BlueMap map for each configured world. Rectangular borders use their configured
corners; round borders use an ellipse with the configured X and Z radii. The
outline updates after border changes and config reloads, and is removed when a
border is cleared. It has a fixed "World border" label and works independently
of the Dynmap display setting. BlueMap is optional: its API and `flow-math` are
compile-only dependencies, with no shaded copy or runtime `libraries` entry.
BlueMap must have a map whose `world:` setting resolves to the Bukkit world for
an outline to appear.

`/wb trim` is disabled on Paper 26.3. Editing region files while Paper holds
them open can conflict with its cache. Stop the server and use an offline region
editor for chunk removal.

This is a continuation / maintained version of the original plugin created by BrettFlan.

The goal of this project is to maintain the original projects fully working operation and add new features to improve upon the ideas
and philosophies of the original project.

## Upstream 1.19 history

Puremin0rez's 1.19 fork added these changes relative to Brettflan's original.
The Paper 26.3 port restrictions above take precedence:
* The world generation fill speed has been significantly increased (at the cost of more memory usage)
* Improvements have been made to better preserving fill progress between restarts / crashes
* Fixes involving height issues and teleports for border checking tasks
* Auto resume for the world generation fill task will now work properly with worlds loaded by [Multiverse-Core](https://www.spigotmc.org/resources/multiverse-core.390/) & [Hyperverse](https://www.spigotmc.org/resources/hyperverse-w-i-p.77550/)
* An incompatibility between Java 8 and Java 9+ was resolved
* Ability to bypass the worldborder via permission `worldborder.allowbypass` as an alternative to the bypass list
* The world trimming feature now supports entity (1.17+) and POI (1.14+) removal

The upstream 1.19 release was a drop-in replacement for Brettflan's original.
The Patriam port targets Paper 26.3 and must be validated before installation.

## How do I obtain it?

You can download stable releases via Github Releases, [located here.](https://github.com/Puremin0rez/WorldBorder/releases)

You can download development builds via Github Actions, [located here.](https://github.com/Puremin0rez/WorldBorder/actions?query=branch%3Amaster+is%3Asuccess) (Github Account Required)

You can compile it by running the following command in the project directory:

```
./gradlew clean build
```

(The jar file will be located in `/build/libs/`)

## Can I use your code?

The original project, and therefore this project, is licensed as [BSD 2-Clause "Simplified" License](https://github.com/Puremin0rez/WorldBorder/blob/master/LICENSE)

## Acknowledgements

* [BrettFlan](https://github.com/Brettflan) for creating the true and tested [WorldBorder](https://github.com/Brettflan/WorldBorder) project that server admins have relied on for years.
* [Contributors](https://github.com/Puremin0rez/WorldBorder/graphs/contributors) for helping improve the project.
* You, for reading this and checking out the project.
