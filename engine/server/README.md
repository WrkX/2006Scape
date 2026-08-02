# SingleScape Game Server

This is the Game Server component of our emulated Runescape environment.
It's responsibility is to provide the instructions for the in-game world, its entities, and respond to player interactions.
Contained within is an implementation of the Runescape network protocol roughly around version 508.
When run, this java application will listen on TCP port 43594.

### Building the project
 - Run from the SingleScape workspace root: `cd engine/server/`
 - `mvn package`
 - `./runServer.sh`
