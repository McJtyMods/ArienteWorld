Original ArienteWorld systems

These Java files are retained byte-for-byte from the origin/1.16 baseline
1895a0e6bc98c1e4a566302809158a9d178a8c8a. They are not a Gradle source set
and are not included in the development runtime or release jar.

The buildable 1.20.1 checkpoint generates cities on basic vanilla noise terrain.
City assets, layout queries and city AI now have active counterparts in
src/main/java; original rail dispatch remains inactive. Restore the remaining
these systems incrementally into src/main/java, adapting the APIs and
comparing behavior with original master (Minecraft 1.12.2). Some systems
were already incomplete or commented out on the intermediate branch.
City definitions, textures, sounds and other resources remain in
src/main/resources for reuse. See ../../PORTING.txt for scope and commands.
