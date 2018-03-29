## Commands

Commands are parameter sets called Setup, Observe, and Wait. A command is created with the source of the command, 
given by a prefix, the name of the command, and an optional ObsId. Parameters are added to the command as needed.

### ObsId

An ObsID, or observation Id, indicates the observation the command is associated with. 
It can be constructed by creating an instance of `ObsId`. 

Scala
:   @@snip [CommandsTest.scala](../../../../examples/src/test/scala/csw/services/messages/CommandsTest.scala) { #obsid }

Java
:   @@snip [JCommandsTest.java](../../../../examples/src/test/java/csw/services/messages/JCommandsTest.java) { #obsid }

### Prefix

The source of the command is given by the prefix, which should be the full name of the component sending the command.
A prefix can be constructed with a string, but must start with a valid subsystem as in [Subsystem](subsystem.html).
A component developer should supply a valid prefix string and the subsystem will be automatically parsed from it.
An example of a valid string prefix is "nfiraos.ncc.trombone".

See below examples:

Scala
:   @@snip [CommandsTest.scala](../../../../examples/src/test/scala/csw/services/messages/CommandsTest.scala) { #prefix }

Java
:   @@snip [JCommandsTest.java](../../../../examples/src/test/java/csw/services/messages/JCommandsTest.java) { #prefix }

### CommandName

Each command has a name given as a string. The `CommandName` object wraps the string name. The string should be
continuous with no spaces.

### Setup Command

This command is used to describe a goal that a system should match. Component developer will require to supply 
following arguments to create a `Setup` command.

 
 * **[Prefix:](commands.html#Prefix)** the source of the command as described above 
 * **[CommandName:](commands.html#CommandName)** a simple string name for the command (no spaces)
 * **[ObsId:](commands.html#ObsId)**  an optional observation Id.
 * **paramSet:** Optional Set of Parameters. Default is empty.
 
Scala
:   @@snip [CommandsTest.scala](../../../../examples/src/test/scala/csw/services/messages/CommandsTest.scala) { #setup }

Java
:   @@snip [JCommandsTest.java](../../../../examples/src/test/java/csw/services/messages/JCommandsTest.java) { #setup }
 
 
### Observe Command

This command describes a science observation. Sent only to Science Detector Assemblies and Sequencers. This
commands will be more fully defined as part of ESW.

Scala
:   @@snip [CommandsTest.scala](../../../../examples/src/test/scala/csw/services/messages/CommandsTest.scala) { #observe }

Java
:   @@snip [JCommandsTest.java](../../../../examples/src/test/java/csw/services/messages/JCommandsTest.java) { #observe }

### Wait Command

This command causes a Sequencer to wait until notified. This command will be defined as part of ESW.

Scala
:   @@snip [CommandsTest.scala](../../../../examples/src/test/scala/csw/services/messages/CommandsTest.scala) { #wait }

Java
:   @@snip [JCommandsTest.java](../../../../examples/src/test/java/csw/services/messages/JCommandsTest.java) { #wait }

### JSON serialization
Commands can be serialized to JSON. The library has provided **JsonSupport** helper class and methods to serialize Setup, Observe and Wait commands.

Scala
:   @@snip [CommandsTest.scala](../../../../examples/src/test/scala/csw/services/messages/CommandsTest.scala) { #json-serialization }

Java
:   @@snip [JCommandsTest.java](../../../../examples/src/test/java/csw/services/messages/JCommandsTest.java) { #json-serialization }

### Unique Key constraint

By choice, a ParameterSet in either **Setup, Observe,** or **Wait** command will be optimized to store only unique keys. In other words, trying to store multiple keys with same name, will be automatically optimized by removing duplicates.

@@@ note

Parameters are stored in a Set, which is an unordered collection of items. Hence, it's not predictable whether first or last duplicate copy will be retained. Hence, cautiously avoid adding duplicate keys.

@@@    

Here are some examples that illustrate this point:

Scala
:   @@snip [CommandsTest.scala](../../../../examples/src/test/scala/csw/services/messages/CommandsTest.scala) { #unique-key }

Java
:   @@snip [JCommandsTest.java](../../../../examples/src/test/java/csw/services/messages/JCommandsTest.java) { #unique-key }

### Cloning a command

Every command that is sent must have a new, unique RunId. A `cloneCommand` method is available for all commands 
which can be used to create a new command from existing parameters, but with a new RunId. 
This is needed in the case where a Setup is defined once because it has no fields that change. Before submitting
get a copy with a new RunId using `cloneCommand`. If a new Setup is created for each use, this command is not
needed.

@@@ note

Any command that is sent needs to have a unique Id as this Id is the one against which the status of the command is 
maintained at the recipient end. This Id can thus be used to query and subscribe the status of the respective command.

@@@  

Scala
:   @@snip [CommandsTest.scala](../../../../examples/src/test/scala/csw/services/messages/CommandsTest.scala) { #clone-command }

Java
:   @@snip [JCommandsTest.java](../../../../examples/src/test/java/csw/services/messages/JCommandsTest.java) { #clone-command }


## Source code for examples

* @github[Scala Example](/examples/src/test/scala/csw/services/messages/CommandsTest.scala)
* @github[Java Example](/examples/src/test/java/csw/services/messages/JCommandsTest.java)