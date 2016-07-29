# SpringoJS

SpringoJS = [Spring](https://spring.io) + [RingoJS](http://ringojs.org)!

Springo provides a minimal framework for incorporating [RingoJS](http://ringojs.org) into a [Spring MVC](http://docs.spring.io/spring/docs/current/spring-framework-reference/html/mvc.html)
application.

Note: RingoJS is built on top of Rhino a very mature Javascript engine that
  runs on the JVM.  In what follows I will sometimes use "Ringo" and "Rhino"
  interchangeably.  In reality Ringo is a *superset* of Rhino that adds (node.js
  style) CommonJS module support to Rhino, along with a variety of modules 
  including an express-style middleware framework called "stick".  The result
  is a very node.js-like framework that runs on the JVM with the biggest
  difference being the use of java style blocking I/O which avoids the 
  frequent use of callbacks as seen in node.js.
  
The approach used in Springo has been used quite successfully at a fortune 500
company to deliver RESTful JSON services using a codebase that started as a
Java Spring application, but evolved into a "hybrid" Spring + Server-Side
Javascript application using both Spring MVC and Ringo/Rhino running
together within a single JVM process.

In this approach we found Ringo's server-side Javascript to be very productive
and brought a "rapid application development tool" flavor to our RESTful service
development compared to using Java with Spring MVC alone.

This was especially true due to Ringo's support for hot deployment of code changes,
and because our development team already had strong Javascript skills since the
developers were previously responsible for end to end development encompassing not
only the server side Spring code but also a Javascript heavy web application.

Incorporating server side Javascript entailed a small learning curve for the
team compared to incorporating an entirely new JVM language such as Groovy or
Scala or Clojure, etc.

Also compared to these other languages, our code was simplified owing to the ease
of working with the RESTful JSON request and response payloads directly in RingoJS,
where it's very easy to *see* the JSON payloads when reading our Javascript code.

This contrasts with the other languages which of necessity must treat JSON as
a "serialization" or "marshalling" format that must be converted into the
other language's data structures and syntax for Groovy/Scala/Clojure/etc.

As the team used RingoJS server side Javascript more, our coding style evolved
into a more Functional style focused on simple data structures and pure
functions in lieu of elaborate Object Oriented design patterns and class
hierarchies. i.e. Our JSON request/response payloads were used directly as our
"data structures" and they were passed in and out of Javascript functions
organized into Ringo's CommonJS modules.  

So though Ringo allows you to call into Java easily (which we leveraged to call
into our legacy Java code), we wrote our RingoJS server-side Javascript code
in a simpler non-stateful/non-Object-Oriented style with less of the ceremony
and verbosity that Java can bring.

### Disclaimer

Though we had great success with this approach the code in this repository
has not been tested with the latest version of RingoJS, and it's been designed
for use with Spring proper not "Spring Boot" which is currently gaining in
popularity compared to Spring proper.  

For using RingoJS with Spring Boot see my other project "BoingoJS".  

Otherwise if you're interested in using this project for real, please contact
me at darren.cruse@gmail.com with your project details.  Assuming it's a good
fit we should upgrade and test SpringoJS with the lastest version of RingoJS,
and I should otherwise improve the documentation and provide a full fledged
example (which I have not currently provided - this repo is currently code
only) to make your life easier.

In addition, before initiating use of SpringoJS on a new project I would heartily
recommend contacting the RingoJS project esp. with an eye toward their progress
(or lack of progress) on upgrading to use Nashorn instead of Rhino as their
underlying Javascript engine.  While we found the maturity of Ringo/Rhino of
benefit on our project, we started several years ago before Nashorn was on the
scene - whereas today Nashorn is the *official* future of Javascript on the JVM.

#### Disclaimer on the Disclaimer

Maybe this goes without saying but...

The major reason for a project to consider an approach like SpringoJS is that
they want to use server side Javascript (for reasons such as I outlined above),
but they require the JVM for such reasons as:  enterprise requirements, legacy
Java code to integrate with, team expertise, the JVM's greater maturity and
"monitor-ability" etc.

To be clear though if a project does *not* require the JVM, they should
consider using the "big dog of server side Javascript"...

i.e. Node.js.  

Because of node's greater popularity and larger community (compared to RingoJS),
Node.js clearly makes sense outside the JVM.

What my experience using the SpringoJS approach taught me is that you *can*
very successfully use server side Javascript to "orchestrate" Spring MVC on
the JVM using RingoJS.  

On a real world project (with fairly high traffic!) that's still
running today.

### SpringoJS Architecture

An app using the SpringoJS approach is a normal Spring MVC application.

But Ringo server-side Javascript can run before, after, or instead of your
Spring MVC "controller" Java code via a servlet filter.

Note:  The SpringoJS servlet filter is unique to SpringoJS - RingoJS normally
  runs as a *servlet* not a *filter*.

The "before" or "after" approach may remind you of "AOP" - in this case you
can use Javascript to quickly/easily provide the "advice" to Spring "controller" code
implemented in Java.

SpringoJS provides the option to deploy the ringojs Javascript files to the
file system - outside the WAR file.  This allows you to hot deploy server-side
Javascript changes without a Java recompile and without a server restart.

In the case where your RingoJS code runs "instead of" Java code, this could mean
you've *duplicated* Java functionality using Javascript (presumably so you could
hot deploy it without a rebuild and Java code deploy as described above).

But the more likely case is you simply chose to implement the functionality in
Javascript instead of Java.  In this case you can think of your application as
truly a hybrid - some parts written in Java and some parts written in
Javascript.  Which parts are written in what is just an implementation detail
unimportant to the clients submitting requests and getting back responses.

The reasons to choose Java versus Javascript are a matter of tradeoffs.  Java
can give faster performance (though most applicatons are I/O bound - waiting for
the network or the database so the slower speed of Rhino Javascript is plenty
fast enough), or Java might be preferred if you're invoking a lot of Java libraries
to handle the request (though invoking Java from Rhino *usually* works pretty  
well anyway).  

Otoh Javascript might be preferred if you've got a Javascript
client (e.g. a browser web page using Javascript) and you want to use the
same Javascript code on the server that you use on the browser.  

Or maybe the choice to use Java versus Javascript is not a technical
one at all - maybe the only available developer when the feature got implemented
was a server side Java developer who doesn't know Javascript.

Or maybe the only available developer was a browser side developer who didn't
know Java (note however that *some* knowledge of Java is helpful when writing
the RingoJS server side Javascript since you often invoke Java code from
your server-side Javascript).

### Major Java Classes

See the javadocs in the java source for more information on the below.

#### RingoJsgiFilter

The RingoJsgiFilter lets you use Ringo Javascript along side Spring MVC in a
single JVM process.

Note:  SpringoJS is not terribly tied to Spring, it should work as well
  with some other Java servlet based web framework.

The Javascript can either:

a.  Generate the response instead of the servlet.

Or:

b.  Delegate generation of the response to the servlet pipeline, e.g
     to let a Spring MVC controller generate the response in Java.

Option a. provides for generating responses fully via Ringo Javascript
without using the Java framework at all.

Option b. esp. when implementing JSON RESTful services, allows for easy
"AOP-ish" (i.e. before/after) modifications in Javascript.  Javascript
can be used to modify (or validate) the request before the Java framework
sees it, and/or modify the response that the other framework generated.

#### Example web.xml config for RingoJsgiFilter

See RingoJS docs for details about the settings you see below.

```
<filter>
  <filter-name>RingoFilter</filter-name>
  <filter-class>org.springo.RingoJsgiFilter</filter-class>
  <init-param>
    <param-name>ringo-home</param-name>
    <param-value>/usr/local/myapp/ssjs</param-value>
  </init-param>
  <!-- reload javascript if the soft link changes -->
  <init-param>
    <param-name>reload-if-modified</param-name>
    <param-value>/usr/local/myapp</param-value>
  </init-param>				
  <init-param>
    <param-name>module-path</param-name>
    <param-value>.,js,js/modules,js/middleware</param-value>				
  </init-param>
  <init-param>
    <param-name>config</param-name>
    <param-value>config</param-value>
  </init-param>
  <init-param>
    <param-name>app</param-name>
    <param-value>app</param-value>
  </init-param>		
  <init-param>
    <param-name>verbose</param-name>
    <param-value>true</param-value>
  </init-param>		
</filter>

<filter-mapping>
  <filter-name>RingoFilter</filter-name>
  <url-pattern>/*</url-pattern>
  <!-- Ensure the rewritten (prefixed) urls pass through this filter -->		 
  <dispatcher>FORWARD</dispatcher>   		
  <!-- And let the filter also be hit directly (for testing, etc) -->		
  <dispatcher>REQUEST</dispatcher>		
</filter-mapping>
```

#### Overriding Ringo Settings Using -D

It's esp. helpful on developer PCs to override the web.xml settings above using
-D java command line options (e.g. web.xml paths might be Unix paths for deployment
while the developer might be developing using Windows).

e.g. the "ringo-home" can optionally be overridden via "-Dscripting.home=X" and/or
"debug" can be overridden via e.g. "-Dscripting.debug=true".

#### BufferedResponseWrapper

The BufferedResponseWrapper class wraps a servlet response and buffers it's output
so that the Javascript interceptor has the option of modifying it before it's sent
back to the client.

This is necessary because in java the standard HttpServletResponse class is designed
to be read-only.

#### RingoModuleBridge

RingoModuleBridge can be used to invoke RingoJS javascript module functions
from java.

You can use Spring to configure the bridge to give access to the functions of
a single module, or to give access to the functions of any module
(in which case you pass the module name when you invoke the function).

The extra arguments passed from Java when calling invokeMethod()
or invokeFunction() are passed to Javascript using Rhino/Ringos
standard rules for "wrapping" Java types as Javascript types.

This means simple types like Strings and Integers go through as
expected, while complex Java Java types go through as their
Java types and must be manipulated using their Java api.

Exceptions:

1.  Java Beans with getters and setters can be accessed using
   Javascript property conventions where e.g. "bean.getFirstName()"
   becomes simply "bean.firstName".

   (this is standard Rhino)

2.  Maps passed as args from Java appear as Javascript objects i.e.
   instead of "map.get('property')" you can do "map.property".

   (this is an enhancement we've made that only applies when invoking
   Javascript via the RingoModuleBridge).

### Javascript Modules

#### springcontext

The springcontext module lets you inject one or more beans from Spring into
the specified Javascript object.

Most commonly, it's expected you would do this near the top of your module e.g.:

    var Spring = require("springcontext");
    Spring.inject(this,
        "managePlatformService",
        "jsonDataService");

The result is that two module scoped variables "managePlatformService"
and "jsonDataService" are added in the current module, pointing at the
beans of the same names configured in Spring.

If the bean names configured in Spring are not to your liking, you can give
them different names by passing an Array containing [<aliasName>, <beanName>]
instead of one or more of the bean names e.g.:

    Spring.inject(this, ["mps","managePlatformService"], "jsonDataService");

Where in this example the variable "mps" is added to your module,
pointing at the "managePlatformService" Spring bean (and "jsonDataService"
is added normally using the "jsonDataService" bean name as the variable name).

Note:  If any of the specified bean names are not found in Spring, a
      NoSuchBeanException is thrown.

### Javascript Middleware

SpringoJS provides custom middleware to modify requests, and generate (then
modify if desired) the responses generated using Spring MVC.

Note:  if you're new to middleware read Ringo's "stick" framework documentation.

### Modify Request (before Spring MVC)

#### modifyrequestparams

Modify the query parameters of the incoming request.

#### modifyrequestbody

Modify the JSON payload of a POST request.

#### modifyservletrequest

Modify the servlet request object for an incoming request.

This can be used e.g. to modify request headers.

### Modify Response (after Spring MVC)

#### modifyresponsebody

Modify the JSON response generated for a request.

#### modifyservletresponse

Modify the servlet response object after generating the response.

This can used e.g. to modify the resulting http status code, etc.

### Generate Response (invoke Spring MVC)

#### runfilterchain

Capture the output of running the servlet filter chain.

### Other

#### jsonerror

Catch (thrown) application errors and generate
a *standard* JSON error response to the RESTful client.

The point of this middleware is that your RESTful service api
should *guarantee* a standard documented error format is being sent to
your client in *all* circumstances (even when unanticipated exceptions
are thrown!).
