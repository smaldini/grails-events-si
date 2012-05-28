### Events API : Spring Integration

Plugin platforms brings an event API to allow decoupled communication within your application and beyond.
If you want to use more features like routing mechanisms or external message brokers, you could also rely on specifics plugins which implement
the platform events API such as Spring Integration (si-events, this plugin).

It configures routes, channel, and gateway for you. It allows grails dev to integrate their events with external systems through Spring Integration adapters.

#### Declaring an event listener
A listener is an handler attached to a topic name, ie 'userLogged' or 'bookArchived'.
This handler can receive and return values as we will see in the 'Sending Events' paragraph.

There are 3 ways to declare listeners :

##### Services artefacts :

You can surround candidate methods with @Listener(String topic. If you don't define explicitly any topic, then the method name will be used :

```groovy
class SomeService{
   @grails.events.Listener('userLogged')
   def myMethod(User user){
      //do something with user
   }

   @grails.events.Listener
   def mailSent(User user){ //use 'mailSent' as topic name
         //do something with user
  }
}
```

##### Inline closures :

Inside services, domains and controllers artefacts, you can call "String addListener(String topic, Closure closure)".
This method returns a listener Id which is under the following format "topic:ClassName#method@hashCode":

```groovy
class SomeController{

   def auth(){
      String listenerId = addListener('userLogged'){User user->
       //do something with user
      }
   }
}
```


##### Custom objects :

You can also declare runtime listeners with any object and method using "String addListener(String topic, Object bean, Method/* or String */ method)".
As previously mentionned, this method returns a listener Id which is under the following format "topic:ClassName#method@hashCode":

```groovy
class SomeController{

   def someService

   def auth(){
      addListener('userLogged', someService, 'myMethod')
   }
}
```

#### Sending events

You have 2 ways of sending events : asynchronously or syncronously. Both methods returns an EventReply object.
EventReply implements Future<Object> and provides 3 usefuls methods :
* List<Object> getValues()
Return as many values as listeners has replied.
* Object getValue()
Return the first element of getValues().
* int size()
Return the replies count.


##### Sync events
Syncronous events can be sent from domains, services and controllers artefacts by using "EventReply event(String topic, Object data)" :

```groovy
class SomeService{
    @Listener('logout')
    def method(User user){
       Date disconnectDate = new Date()

       //do something very long with user

       return disconnectDate
    }
}

class SomeController{

   def logout(){
      def reply = syncEvent('logout', session.user)
      render reply.value  //display disconnectDate
   }
}
```

##### Async events
Asyncronous events can be sent from domains, services and controllers artefacts by using "EventReply eventAsync(String topic, Object data)" :

```groovy
class SomeService{
    @Listener('logout')
    def method(User user){
       Date disconnectDate = new Date()

       //do something with user

       return disconnectDate
    }
}

class SomeController{

   def logout(){
      def reply = eventAsync('logout', session.user)
      render reply.value //block the thread until event response and display disconnectDate
   }
}
```

##### Waiting replies

In domains, services and controllers artefacts you can wait for events using "EventReply[] waitFor(EventReply... eventReplies)".
This method is rather useless in a sync scenario. It accepts as many events replies you want and returns the same array
for functiunal programming style. :

```groovy
class SomeService{
    @Listener('logout')
    def method(User user){
       Date disconnectDate = new Date()

       //do something with user

       return disconnectDate
    }
}

class SomeController{

   def logout(){
      def reply = eventAsync('logout', session.user)
      def reply2 = eventAsync('logout', session.user)
      def reply3 = eventAsync('logout', session.user)

      waitFor(reply,reply2,reply3).each{EventReply reply->
        render reply.value +'</br>'
      }
   }
}
```


#### GORM events (Grails 2 only)

You can listen all the grails 2 gorm events using the same topic name than domain method handler described in grails documentation.
The listener argument has to be typed to specify the domain that the handler listens for. If the gorm event can be cancelled like with beforeInsert or beforeValidation,
the handler can return a false boolean to discard the event :

```groovy
class SomeService{
   @Listener('beforeInsert')
   def myMethod(User user){
      //do something with user
      false //cancel the current insert
   }
}
```

#### Removing listeners

To remove listeners, just use "int removeListeners(String listenerIdPattern)". The argument allows you to filter listeners by using the listener id pattern.
For instance you can use both "topic" and "topic:ClassName#method".
The method returns the number of deleted listeners :

```groovy
class SomeController{

   def logout(){
      println removeListeners('userLogged') //remove all listeners for topic 'userLogged'
      println removeListeners('statistics:org.sample.StatisticsService') // remove all listeners for topic 'statistics' and class 'StatisticsService'
   }
}
```

#### Counting listeners

To count listeners, just use "int countListeners(String listenerIdPattern)". The argument allows you to filter listeners by using the listener id pattern.
For instance you can use both "topic" and "topic:ClassName#method".
The method returns the number of listeners :


```groovy
class SomeController{

   def logout(){
      println countListeners('userLogged') //count all listeners for topic 'userLogged'
      println countListeners('statistics:org.sample.StatisticsService') // count all listeners for topic 'statistics' and class 'StatisticsService'
   }
}