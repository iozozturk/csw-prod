# Creating a Component

This tutorial helps in creating a CSW component in Scala/Java. CSW components depend on the `csw-framework` package,
which can be found @ref:[here](./framework.md). This tutorial discusses constructing a HCD, 
but the principles apply to an Assembly as well. We will be constructing the Assembly in the next tutorial section @ref:[Working with Mulitple Components](./multiple-components.md). 

## Anatomy of Component
    
A component consists of a supervisor actor, a Top Level Actor, a component handler and one or more worker actors. From all these, `csw-framework`
provides supervisor actor, a Top Level Actor and abstract class of handlers. Component developers are expected to implement this handler which also
acts as a gateway from framework to component code.   
     
### Supervisor

A Supervisor actor is the actor first started for any component. The main responsibilities that supervisor performs is as follows:

-   Implement and manage the component lifecycle for the TLA and for the rest of the system (see [Lifecycle](#lifecycle) below).
-   Register itself with location service.
-   Provide an administrative interface to the component to the rest of the system. For
instance, the Container can perform certain administrative communication with the Supervisor such as restart or shutdown.
-   Allow components outside of the Supervisor and TLA to monitor the lifecycle state of the TLA. This is particularly useful for testing. The test needs to know that the component is
ready before it starts its test actions.

@@@ note { title=Note }

Because the Supervisor registers itself with location service, it serves as the gateway for all incoming communications from external components/entities.

@@@

The source code of supervisor actor can be found [here](https://github.com/tmtsoftware/csw-prod/blob/master/csw-framework/src/main/scala/csw/framework/internal/supervisor/SupervisorBehavior.scala)

### Top level actor

While the Supervisor works as the external interface for the component and the manager of Lifecycle, the functional implementation
of a component is implemented in a Top Level Actor (TLA), spawned by supervisor actor for any component. 
However, the developer is not expected to implement TLA code directly.  Instead, the functionality of the TLA is added by
implementing the `ComponentHandlers` abstract class, consisting of a list of a methods, or `hooks`, called by the TLA during specific lifecycle and command events (see [Handlers](#handlers)).
The `ComponentHandlers` implementation is specified during constructing using that factory (see [Constructing The Component](#constructing-the-component)) 

The source code of the Top Level Actor can be found [here](https://github.com/tmtsoftware/csw-prod/blob/master/csw-framework/src/main/scala/csw/framework/internal/component/ComponentBehavior.scala).

### Handlers

The following hooks should be overridden in your ComponentHandlers implementation class:

-   `initialize`: called when component is starting up, prior to be put into the Running state.
-   `validateCommand`: called when component receives a command.  (see [Validation](#validation))  
-   `onSubmit`: called on Submit command if validateCommand returns `Accepted`.
-   `onOneway`: called on Oneway command if validateCommand returns `Accepted`.
-   `onGoOffline`: called when component receives external message to go offline.
-   `onGoOnline`: called when component receives external message to go online.
-   `onLocationTrackingEvent`: called when a tracked dependency changes location state. (see @ref:[Tracking Dependencies](./multiple-components.md#tracking-dependencies))
-   `onShutdown`: called when component is shutting down.

The source code of `ComponentHandlers` can be found [here](https://github.com/tmtsoftware/csw-prod/blob/master/csw-framework/src/main/scala/csw/framework/scaladsl/ComponentHandlers.scala). 

More details about handler significance and invocation can be found @ref:[here](./framework.md#handling-lifecycle)

@@@ note { title=Note }

If the component developer wishes to write the handler implementation in java, then he/she needs to implement the java version of `ComponentHandlers`
which is `JComponentHandlers`. The source code of `JComponentHandlers` can be found [here](https://github.com/tmtsoftware/csw-prod/blob/master/csw-framework/src/main/scala/csw/framework/javadsl/JComponentHandlers.scala).
Any further reference to `ComponentHandlers` should implicitly also apply to `JComponentHandlers`.

@@@

## Constructing the Component

After writing the handlers, component developer needs to wire it up with framework. In order to do this, developer 
needs to implement a `ComponentBehaviorFactory`. This factory should to be configured in configuration file for
the component (see example below). The `csw-framework` then picks up the full path of
`ComponentBehaviorFactory` from configuration file and spawns the component handlers using this factory as a process of
booting a component. The factory is instantiated using java reflection.

@@@ note { title=Note }

If using the gitter8 template, this factory class will be implemented for you.

@@@

The sample code to implement the `ComponentBehaviorFactory` can be found @ref:[here](./framework.md#creating-components) 

### Component Configuration (ComponentInfo)

Component configuration contains details needed to spawn a component. This configuration resides in a configuration file
for a particular component. The sample for HCD is as follows:

```
name = "GalilHcd"
componentType = hcd
behaviorFactoryClassName = "org.tmt.nfiraos.galilhcd.GalilHcdBehaviorFactory"
prefix = "galil.hcd"
locationServiceUsage = RegisterOnly
``` 

@@@ note { title=Note }

`behaviorFactoryClassName` refers to class name of the concrete implementation of `ComponentBehaviorFactory`, which is `GalilHcdBehaviorFactory` in above example.

@@@

The `name` and `componentType` is used to create the `ComponentId` representing a unique component in location service.

The `locationServiceUsage` is used by the Supervisor actor to decide whether to only register a component with location service or register and track other components.
  
The configuration file is parsed to a `ComponentInfo` object and injected in the Supervisor actor. It is then injected in `ComponentHandlers` while spawning a component.

The configuration can also contain a list of components and services it wishes to track as dependencies. See @ref:[Tracking Dependencies](./multiple-components.md#tracking-dependencies).

More details about `ComponentInfo` can be found @ref:[here](./framework.md#describing-components).

A sample configuration file can be found [here](https://github.com/tmtsoftware/csw-prod/blob/master/csw-benchmark/src/main/resources/container.conf).

## Lifecycle 

A component can be in one of the following states of lifecycle:

-   Idle
-   Running
-   RunningOffline
-   Restart
-   Shutdown
-   Lock

### Idle

The component initializes in the idle state. Top level actor calls the `initialize` hook of `ComponentHandlers` as first thing on boot-up.
Component developers write their initialization logic in this hook. The logic could also do things like accessing the configuration service
to fetch the hardware configurations to set the hardware to default positions.

After the initialization, if the component would have configured `RegisterAndTrack` for `locationServiceUsage` then the Top Level Actor will start tracking
the `connections` configured for that component. This use case is mostly applicable for Sequencers and Assemblies. HCDs mostly will have `RegisterOnly`
configured for `locationServiceUsage`.

The Supervisor actor will now register itself with location service.  Registering with location service will notify other components
tracking this component with a `LocationUpdated` event containing a `Location` with a reference to the Supervisor actor.

After successful registration, the component will transition to `Running` state. 

### Running

When the supervisor actor receives `Initialized` message from the Top Level Actor after successful initialization, it registers itself with location service and transitions
the component to `Running` state. Running state signifies that the component is accessible via location service, which allows other entities to communicate
with it by sending commands via messages. Any commands received by supervisor actor will be
forwarded to the Top Level Actor for processing.

### RunningOffline

When the Supervisor actor receives `GoOffline` message, it transitions the component to `RunningOffline` state and forwards it to the Top Level Actor. The Top Level Actor then calls
`onGoOffline` hook of `ComponentHandlers`.

If `GoOnline` message is received by the Supervisor actor then it transits the component back to `Running` state and forwards it to the Top Level Actor. The Top Level Actor then calls
`onGoOnline` hook of `ComponentHandlkers`.

@@@ note { title=Note }

In `RunningOffline` state, if any command is received, it is forwarded to underlying component hook through the Top Level Actor. It is then the responsibility of
the component developer to check the `isOnline` flag provided by `csw-framework` and process the command accordingly.  

@@@

### Restart

When the Supervisor actor receives a `Restart` message, it will transit the component to the `Restart` state. Then, it will unregister itself from location service so that other components
tracking this component will be notified and no commands are received while restart is in progress.

Then, the Top Level Actor is stopped and postStop hook of the Top Level Actor will call the `onShutdown` hook of `ComponentHandlers`. Component developers are expected to write 
any cleanup of resources or logic that should be executed for graceful shutdown of component in this hook.  

After successful shutdown of component, the Supervisor actor will create the Top Level Actor again from scratch.  This will cause the `initialize` hook of `ComponentHandlers` to be called
again. After successful initialization of component, the Supervisor actor will register itself with location service.

### Shutdown

When the Supervisor actor receives a `Shutdown` message, it transitions the component to the `Shutdown` state.  Any commands received while shutdown is in progress will be ignored.
Then, it will stop the Top Level Actor. The postStop hook of the Top Level Actor will call the `onShutdown` hook of `ComponentHandlers`. Component developers are expected to write 
any cleanup of resources or logic that should be executed for graceful shutdown of component in this hook.

### Lock

When the Supervisor actor receives a `Lock` message, it transitions the component to the `Lock` state. Upon locking, the Supervisor will only accept the commands received from the component
that locked the component and ignore all others.

In the `Lock` state, messages like `Shutdown` and `Restart` will also be ignored.  A component must first be unlocked to accept these commands.

`Lock` messages are constructed with a duration value specified.  When this duration expires, the component
will automatically be unlocked.  A component can be manually unlocked by sending an `Unlock` message.

## Logging

`csw-framework` will provide a `LoggerFactory` as dependency injection in constructor of `ComponentHandlers`. The `LoggerFactory` will have the component's name predefined in
it. The component developer is expected to use this factory to log statements.

More details on how to use `LoggerFactory` can be found @ref:[here](../services/logging.md#enable-component-logging). 

## Receiving Commands

A command is something that carries some metadata and a set of parameters. A component sends message to other components using `commands`.
Various kinds of commands are as follows:

-   Setup : Contains goal, command, or demand information to be used to
            configure the target OMOA component.
-   Observe: Contains goal or demand information to be used by a detector.
             system. Properties and their value types will be standardized
             by the ESW for the ESW PDR.
-   Wait: Sequencer only.  Instructs a sequencer to pause until told to continue.

More details about creating commands can be found @ref:[here](./messages.md#setup-command).

Whenever a command is sent to a component it is wrapped inside a command wrapper. There are two kinds of command wrapper:

-   Submit: A command is wrapped in submit when the completion result is expected from receiver component 
-   Oneway: A command is wrapped in oneway when the completion of command is not expected from receiver component but is determined by sender component by subscribing to receiver component's
            state

### Validation

When a command is received by a component, the Top Level Actor will call the `validateCommand` hook of `ComponentHandlers`. Component developers are expected to perform appropriate
validation of command, whether it is valid to execute, and return a `CommandResponse`. The `CommandResponse` returned from this hook will be sent back to sender directly by `csw-framework`.

The logic in `validateCommand` hook can used to handle commands of various durations. If the command can be executed immediately, then the
component developer can return a final response directly in the validation step using a `CompletedWithResult` command response.  
This should be only used for commands that require a very small amount of time to execute. 
If the command will take longer, then component developer should return an intermediate response `Accepted`
or `Invalid` specifying whether the command is valid to be executed or not, and process the command in the `onSubmit` or `onOneway` handlers (see [Command Response](#command-response)).

Different types of command responses and their significance can be found @ref:[here](./command.md#command-based-communication-between-components).

### Command Response

The response returned from `validateCommand` hook of `ComponentHandlers` will be received by the Top Level Actor, who then sends the response back to sender. If the
response returned was `Accepted`, then it either calls the `onSubmit` hook or the `onOneway` hook of `ComponentHandlers` depending on the wrapper(submit or oneway) in which the command
was received. 

If the command was received as a `Submit`, then the Top Level Actor adds the response returned from the `validateCommand` hook in the `CommandResponseManager`.
If the response was `Accepted`, the TLA then calls the `onSubmit` hook of `ComponentHandlers`.  

In case the command received by a component a `Oneway`, the response is not added to the `CommandResponseManager`, and the `onOneway`
hook of `ComponentHandlers` is called.

The `CommandResponseManager` is responsible for managing and bookkeeping the command status of long running submit commands.
The sender of the command (and any component, really) can query the command statuses or subscribe to changes in command statuses using `CommandService`. 

The `CommandService` class provides helper methods for communicating with other components, and should be a component's primary means of sending
commands to other components. This will be described in the next tutorial section, @ref:[Sending Commands](./multiple-components.md#sending-commands).

When the `onSubmit` hook is called, it is the responsibility of component developers to update the status of the received command in the `CommandResponseManager` as it changes. The instance
of commandResponseManager is provided in `ComponentHandlers` which should be injected in any worker actor or other actor/class created for the component.   

More details on methods available in `CommandResponseManager` can be found @ref:[here](./framework.md#managing-command-state).

## Building and Running component in standalone mode

Once the component is ready, it is started using the `ContainerCmd` object in standalone mode. The details about starting the `ContainerCmd` in standalone mode can be found [here](https://tmtsoftware.github.io/csw-prod/framework/deploying-components.html).

To run the component using the deployment package, perform the following steps:

-   Run `sbt <project>/universal:packageBin`. This will create self contained zip in `<project>/target/universal` directory
-   Unzip generated zip file and enter into bin directory
-   Run the `./<project>-cmd-app --local --standalone <path-to-local-config-file-to-start-the-component>`

@@@ note { title=Note }

CSW Location Service cluster seed must be running, and appropriate environment variables set to run apps.
See https://tmtsoftware.github.io/csw-prod/apps/cswclusterseed.html.

@@@

