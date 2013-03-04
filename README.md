# Actors

The `@Actor`annotation is used to trigger bytecode weaving to insert code 
to allow asynchronous execution.
 
## Async method using @Inbox annotation 

The `@Inbox` annotation is used to mark methods to be instrumented to put 
the execution on an actor queue instead of being executed immediately. 

Only methods returning `void`and `Future<>` may be annotated with the 
`@Inbox`. This is verified on class loading better than runtime but not 
on compile time.
  
### void return async method
 
   @Actor
   class State {
     int counter = 0;
     
     @Inbox
     public void increase() {
       couunter++;
     }
   }
   
### Future<> return async method

### Thread pools and core locality optimizations

When a thread pool is used to execute tasks in the actor system recent work 
is prioritized to be executed on same thread to optimize the usages of 
caches in a multicore CPU.

## Actor system

### Spawner

When a new actor is created the spawning actor is refered as a spawner from 
the new actor. Any failure within the spawned actor will be reported upward 
through the chain of spawners. 

## Supervisor

A spawner may act on failures from spawned actors.

    @Actor
    public class A implements Supervisor<B,IOException> {
    
      @Override
	  public boolean actorFailure(B actor, IOException exception) {
	    actor.reset();
	    return true;
	  }
	  
	  @Inbox
	  public void process() {
	    new B().process();
	  }
	  
    }