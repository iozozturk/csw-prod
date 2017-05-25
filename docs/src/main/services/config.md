# Configuration service

Configuration Service provides a centralized persistent store for any configuration file used in the TMT Software System. All versions of configuration files are retained providing a historical record of each configuration file.

This service will be part of observatory cluster and exposes Rest endpoints that can be accessed over HTTP. Component developers can use the csw-config-client library in their code. The library wraps the low level communication with ConfigServer and exposes simple to use methods to access and manage configuration files.  
 
## Artifacts

sbt
:   @@@vars
    ```scala
    libraryDependencies += "org.tmt" %% "csw-config_$scala.binaryVersion$" % "$version$"
    ```
    @@@

maven
:   @@@vars
    ```xml
    <dependency>
     <groupId>org.tmt</groupId>
     <artifactId>csw-config_$scala.binaryVersion$</artifactId>
     <version>$version$</version>
     <type>pom</type>
    </dependency>
    ```
    @@@

gradle
:   @@@vars
    ```gradle
    dependencies {
      compile group: "org.tmt", name: "csw-config_$scala.binaryVersion$", version: "$version$"
    }
    ```
    @@@

## Rules and checks
* The config file path must not contain `!#<>$%&'@^``~+,;=` or `any whitespace character`    
* If the input file is > 10MB or has lot of non ASCII characters, then for optimization, server will archive it in `annex` store.
* Large and binary files can be forced to go to 'annex' store by using a `annex=true` flag in `create` operation. 
* API functions accept date-time values in UTC timezone. (e.g. 2017-05-17T08:00:24.246Z) 

## Model classes
* **ConfigData** : Represents the contents of the files being managed. It wraps stream of ByteString.    
* **ConfigFileInfo** : Represents information about a config file stored in the config service.    
* **ConfigFileRevision** : Represents information about a specific version of a config file.    
* **ConfigId** : Represents identifier associated with a revision of configuration file, often generated by `create` or `update` methods.    
* **ConfigMetadata** : Represents metadata information about ConfigServer.    
* **FileType** : Represents the type of storage for a configuration file. Currently two types are supported `Normal`(small, text files) and `Annex`(Large, Binary files).
 
## API flavours

csw-config-client offers two APIs.

* **clientAPI** : Expected to be consumed by component developers. Available functions are: `{exists | getActive}`    
* **adminAPI**  : Full functionality exposed by ConfigServer is available with this API. Expected to be used administrators. Available functions are: `{create | update | getById | getLatest | getByTime | delete | list | history | historyActive | setActiveVersion | resetActiveVersion | getActiveVersion | getActiveByTime | getMetadata | exists | getActive}`

## Accessing clientAPI and adminAPI

ConfigClientFactory exposes functions to get clientAPI and adminAPI. Both the functions require LocationService instance which is used to resolve ConfigServer.

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #create-api }

Java
:   @@snip [JConfigClientDemoExample.java](../../../../csw-config-client/src/test/java/csw/services/config/client/javadsl/demo/JConfigClientDemoExample.java) { #create-api }

## exists

Function checks if the file exists at specified path in the repository. If it exists it returns Future of Boolean

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #exists }

Java
:   @@snip [JConfigClientDemoExample.java](../../../../csw-config-client/src/test/java/csw/services/config/client/javadsl/demo/JConfigClientDemoExample.java) { #exists }

## getActive

Function retrieves currently active file for a given path from config service. It returns a Future of Option of ConfigData.

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #declare_string_config #getActive }

Java
:   @@snip [JConfigClientDemoExample.java](../../../../csw-config-client/src/test/java/csw/services/config/client/javadsl/demo/JConfigClientDemoExample.java) { #declare_string_config #getActive }


## create

Takes input ConfigData and creates the configuration in the repository at a specified path

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #create }

Java
:   @@snip [JConfigClientDemoExample.java](../../../../csw-config-client/src/test/java/csw/services/config/client/javadsl/demo/JConfigClientDemoExample.java) { #create }


## update

Takes input ConfigData and overwrites the configuration specified in the repository

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #update }

## delete

Deletes a file located at specified path in the repository

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #delete }

## getById

Returns file at a given path and matching revision Id

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #getById }

## getLatest

Returns the latest versio of file stored at the given path.

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #getLatest }

## getByTime

Gets the file at the given path as it existed at a given time-instance. Note:    

* If time-instance is before the file was created, the initial version is returned.    
* If time-instance is after the last change, the most recent version is returned.    

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #getByTime }

## list

For a given FileType(Annex or Normal) and an optional pattern string, it will list all files whose path matches the given pattern. Some pattern examples are: "/path/hcd/*.*", "a/b/c/d.*", ".*.conf", ".*hcd.*"

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #list }

## history

Returns the history of revisions of the file at the given path for a range of period specified by `from` and `to`. The size of the list can be restricted using `maxResults`.
Returns the history of active revisions of the file at the given path for a range of period specified by `from` and `to`. The size of the list can be restricted using `maxResults`.

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #history }

## Managing active versions

Following API functions are available to manage the active version of a config file. In it's lifetime a config file undergoes many revisions. An active version is a specific revision from a file's history and it is set by administrators.   

* **historyActive** : Returns the history of active revisions of the file at the given path for a range of period specified by `from` and `to`. The size of the list can be restricted using `maxResults`.    
* **setActiveVersion** : Sets the "active version" to be the version provided for the file at the given path. If this method is never called in a config's lifetime, the active version will always be the version returned by `create` function.    
* **resetActiveVersion** : Resets the "active version" of the file at the given path to the latest version.    
* **getActiveVersion** : Returns the revision Id which represents the "active version" of the file at the given path.    
* **getActiveByTime** : Returns the content of active version of the file existed at given instant 

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #active-file-mgmt }

## getMetaData

Used to get metadata information about config service. It includes:
    
* repository directory    
* annex directory    
* min annex file size    
* max config file size    

Scala
:   @@snip [ConfigClientDemoExample.scala](../../../../csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala) { #getMetadata }

Java
:   @@snip [JConfigClientDemoExample.java](../../../../csw-config-client/src/test/java/csw/services/config/client/javadsl/demo/JConfigClientDemoExample.java) { #getMetadata }

## Source code for examples

* @github[Scala Example](/csw-config-client/src/test/scala/csw/services/config/client/scaladsl/demo/ConfigClientDemoExample.scala)
* @github[Java Example](/csw-config-client/src/test/java/csw/services/config/client/javadsl/demo/JConfigClientDemoExample.java)