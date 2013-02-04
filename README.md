actor

On creation of an @Actor marked class just a proxy is created and the creation of the object with state is put on queue.
When task is finished it is quite possible the objects just created will be created within same thread only when the system is unbalanced the creation task quick may be picked from another thread but then taken from from the oldest creation tasks to have least thread context association.

When an actor on another thread is called using a reference to this only thread and object id is passed and an new proxy will be created on the other thread if not only created but that only the other thread know since the actor may not even be createdt yet and stolen by another thread.
 
