Background: VCAIS is a very old, unmaintained case management software. As is the case sometimes with legacy software, much of it refuses to die. 

I am leaving these snippets in case it comes in handy for the next person who has to deal with the .nsf format or migrating away from VCAIS.

The only reliable way I have found to run programs against lotus is on a windows environment inside of the lotus notes program (notes.exe). The opensource community has not as of yet been able to fully document the file format. Though I will shout out [libnsfdb](https://github.com/libyal/libnsfdb) as having made what I perceive as the most progress with it.

Your user must have sufficient access in order to create and run what lotus calls "Agents".

If you can create a new agent in the notes application, you should be able to use these Java programs.

These agent will produce a ".dxl" dump which is at least plain text. 
