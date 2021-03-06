# Configuration Service

Configuration Service provides a centralized persistent store for any configuration file used in the TMT Software System. 
All versions of configuration files are retained, providing a historical record of each configuration file.

Note that in order to use the APIs described here, the [Location Service](../services/location.html)
([csw-cluster-seed](../apps/cswclusterseed.html))
and [Configuration Service Server](../apps/cswonfigserverapp.html) needs to be running somewhere in the local network
and the necessary configuration, environment variables or system properties should be defined to point to the 
correct host and port number(s) for the Location Service seed nodes.

This service will be part of the observatory cluster and exposes Rest endpoints that can be accessed over HTTP.
Component developers can use the csw-config-client library in their code.
The library wraps the low level communication with Configuration Service Server and exposes simple to use methods to access and
manage configuration files.

## Dependencies

To use the Configuration Service without using the framework, add this to your `build.sbt` file:

sbt
:   @@@vars
    ```scala
    libraryDependencies += "org.tmt" %% "csw-config-client" % "$version$"
    ```
    @@@

## Rules and Checks
* The config file path must not contain `!#<>$%&'@^``~+,;=` or `any whitespace character`    
* If the input file is > 10MB or has lot of non ASCII characters, then for optimization, server will archive it in `annex` store.
* Large and binary files can be forced to go to 'annex' store by using a `annex=true` flag in `create` operation. 
* API functions accept date-time values in UTC timezone. (e.g. 2017-05-17T08:00:24.246Z) 

## Model Classes
* **ConfigData** : Represents the contents of the files being managed. It wraps stream of ByteString.    
* **ConfigFileInfo** : Represents information about a config file stored in the config service.    
* **ConfigFileRevision** : Represents information about a specific version of a config file.    
* **ConfigId** : Represents identifier associated with a revision of configuration file, often generated by `create` or `update` methods.    
* **ConfigMetadata** : Represents metadata information about ConfigServer.    
* **FileType** : Represents the type of storage for a configuration file. Currently two types are supported `Normal`(small, text files) and `Annex`(Large, Binary files).
 
## API Flavors

The Configuration Service is used to provide the runtime settings for components.  When a component is started, it will 
use a limited "clientAPI" to obtain the "active" configuration from the Configuration Service, and use those settings 
for its execution.

To change the active configuration, an administrative tool with access to the full "admin API" must 
be used. These tools would have the ability to create, delete, and update configurations, as well as retrieve past 
configurations and their history. Any time a new configuration is to be used by a component, the user must use one of 
these tools (via [CLI](../apps/cswconfigclientcli.md), perhaps) to set the active configuration for a component.  Since a history of active configurations 
is maintained by the service, the settings of each component each time it is run can be retrieved, and the system 
configuration at any moment can be recreated.

* **clientAPI** : Must be used in Assembly and HCD components. Available functions are: `{exists | getActive}`    
* **adminAPI**  : Full functionality exposed by Configuration Service Server is available with this API. Expected to be used administrators. Available functions are: `{create | update | getById | getLatest | getByTime | delete | list | history | historyActive | setActiveVersion | resetActiveVersion | getActiveVersion | getActiveByTime | getMetadata | exists | getActive}`

@@@ warning { title="Component developers must use clientAPI." }
    
@@@

## Accessing clientAPI and adminAPI

ConfigClientFactory exposes functions to get clientAPI and adminAPI. Both the functions require Location Service instance which is used to resolve ConfigServer.

@@@ note

Components should only use the client API.  The Admin API may be used from an engineering user interface.
The [CSW Config Client CLI application](../apps/cswconfigclientcli.md) is provided with this functionality.

@@@

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #create-api }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #create-api }

## exists

Function checks if the file exists at specified path in the repository. If it exists it returns Future of Boolean

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #exists }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #exists }

## getActive

Function retrieves currently active file for a given path from config service. It returns a Future of Option of ConfigData.

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #declare_string_config #getActive }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #declare_string_config #getActive }


## create

Takes input ConfigData and creates the configuration in the repository at a specified path

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #create }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #create }


## update

Takes input ConfigData and overwrites the configuration specified in the repository

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #update }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #update }

## delete

Deletes a file located at specified path in the repository

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #delete }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #delete }

## getById

Returns file at a given path and matching revision Id

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #getById }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #getById }

## getLatest

Returns the latest versio of file stored at the given path.

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #getLatest }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #getLatest }


## getByTime

Gets the file at the given path as it existed at a given time-instance. Note:    

* If time-instance is before the file was created, the initial version is returned.    
* If time-instance is after the last change, the most recent version is returned.    

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #getByTime }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #getByTime }


## list

For a given FileType(Annex or Normal) and an optional pattern string, it will list all files whose path matches the given pattern. Some pattern examples are: "/path/hcd/*.*", "a/b/c/d.*", ".*.conf", ".*hcd.*"

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #list }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #list }

## history

Returns the history of revisions of the file at the given path for a range of period specified by `from` and `to`. The size of the list can be restricted using `maxResults`.
Returns the history of active revisions of the file at the given path for a range of period specified by `from` and `to`. The size of the list can be restricted using `maxResults`.

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #history }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #history }

## Managing active versions

Following API functions are available to manage the active version of a config file. In its lifetime a config file undergoes many revisions. An active version is a specific revision from a file's history and it is set by administrators.   

* **historyActive** : Returns the history of active revisions of the file at the given path for a range of period specified by `from` and `to`. The size of the list can be restricted using `maxResults`.    
* **setActiveVersion** : Sets the "active version" to be the version provided for the file at the given path. If this method is never called in a config's lifetime, the active version will always be the version returned by `create` function.    
* **resetActiveVersion** : Resets the "active version" of the file at the given path to the latest version.    
* **getActiveVersion** : Returns the revision Id which represents the "active version" of the file at the given path.    
* **getActiveByTime** : Returns the content of active version of the file existed at given instant 

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #active-file-mgmt }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #active-file-mgmt }

## getMetaData

Used to get metadata information about config service. It includes:
    
* repository directory    
* annex directory    
* min annex file size    
* max config file size    

Scala
:   @@snip [ConfigClientExampleTest.scala](../../../../examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala) { #getMetadata }

Java
:   @@snip [JConfigClientExampleTest.java](../../../../examples/src/test/java/csw/services/config/JConfigClientExampleTest.java) { #getMetadata }

## Source code for examples

* @github[Scala Example](/examples/src/test/scala/csw/services/config/ConfigClientExampleTest.scala)
* @github[Java Example](/examples/src/test/java/csw/services/config/JConfigClientExampleTest.java)
