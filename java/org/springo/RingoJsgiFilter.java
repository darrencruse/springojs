/**
 * The RingoJsgiFilter is intended when using Ringo along side Spring MVC
 * (i.e. in a single JVM process).
 *
 * Note:  Springo is not terribly tied to spring, if you want to use it
 *   with some other java servlet based web framework, you can mentally
 *   replace references below to "spring" with the name of your framework.
 *
 * This filter allows requests to be intercepted and handled via javascript
 * using Ringo.
 *
 * The javascript can either:
 *
 * a.  Generate the response instead of the servlet.
 *
 * Or:
 *
 * b.  Delegate generation of the response to the servlet pipeline, e.g.
 *     to let a Spring MVC controller generate the response in java.
 *
 * Option a. provides for generating responses fully via Ringo javascript
 * without using the java framework at all.
 *
 * Option b. esp. when implementing JSON RESTful services, allows for easy
 * "AOP-ish" (i.e. before/after) modifications in javascript.  Javascript
 * can be used to modify (or validate) the request before the java framework
 * sees it, and/or modify the response that the other framework generated.
 *
 * @author darrencruse (https://github.com/darrencruse)
 */

package org.springo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import java.lang.reflect.Field;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.ringojs.engine.ReloadableScript;
import org.ringojs.engine.RhinoEngine;
import org.ringojs.engine.SyntaxError;
import org.ringojs.jsgi.JsgiRequest;
import org.ringojs.repository.FileRepository;
import org.ringojs.repository.Repository;
import org.ringojs.repository.Resource;
import org.ringojs.repository.WebappRepository;
import org.ringojs.tools.RingoConfiguration;
import org.ringojs.tools.RingoRunner;
import org.ringojs.util.StringUtils;

import org.springo.BufferedResponseWrapper;

public class RingoJsgiFilter implements Filter {

 private String module = null;
 private Object function = null;
 private RhinoEngine engine = null;
 private JsgiRequest requestProto = null;

 private FilterConfig filterConfig = null;

 // This allows the RingoModuleBridge to use the same
 // RhinoEngine the filter does, as configured in web.xml
 public static RingoJsgiFilter instance = null;

 private static Logger log = Logger.getLogger(RingoJsgiFilter.class);

 private static boolean ringoEnabled = true;

 // This is the path to *some file* that changes whenever new
 // javascript is deployed:
 private String triggerReloadsFilePath = "";

 private long triggerReloadsFileLastModified = -1;

 public final static String JSGI_INTERCEPTED_FILTER_CHAIN = "ringo.javax.servlet.filter.chain";

 /**
  * Initialize using our custom config which allows the "ringo-home"
  * to optionally be overridden via "-Dscripting.home=X" and/or
  * "debug" to be overridden via e.g. "-Dscripting.debug=true".
  *
  * If the -Dscripting.home override isn't set and the "ringo-home"
  * init-parm in web.xml isn't pointing to an actual directory where
  * ringo's installed, the filter disables itself but otherwise
  * keeps things working by running the filter chain but without
  * executing any javascript.
  */
 public void init(FilterConfig config) throws ServletException {

  this.filterConfig = config;

  if (getLoadRingoOnStartup(config)) {
   this.engine = createEngine(config, false);
  }
 }

 private Repository getHomeRepository(String ringoHome, ServletContext servletContext) {
  Repository home = null;
  try {
   home = new WebappRepository(servletContext, ringoHome);
   if (!home.exists()) {
    home = new FileRepository(ringoHome);
    log.warn("Resource \"" + ringoHome + "\" not found, " + "reverting to file repository " + home);
   }
  } catch (Exception e) {
   log.error("Could not create Repository for RINGO_HOME at: " + ringoHome);
  }
  return home;
 }


 /**
  * Create/recreate the RhinoEngine.
  *
  * This is called on server start as well as when a new script
  * deployment occurs (as recognized due to a change in the modification
  * date of the parent directory of the scripts directory).
  *
  * Note Ringo out-of-the-box is designed to auto-detect such changes,
  * but some deployment approaches where a soft link is used to point
  * at the new deployment (yet keeping the old deployment files) could
  * confuse ringo.
  *
  * It seems in that approach the file descriptors remain pointed at the
  * old files even after the soft link has changed to the new files.
  *
  * @return
  */
 public synchronized RhinoEngine createEngine(FilterConfig config, boolean onlyIfChanged) throws ServletException {

  RhinoEngine theEngine = null;

  if (onlyIfChanged && this.engine != null && !triggerReloadsFileHasChanged()) {
   // nothing's changed - this was probably a second thread that saw a change
   // while a prior thread was already creating the engine.
   return this.engine;
  }

  log.info("Loading the Ringo/Rhino Engine...");

  String ringoHome = getRingoHome(config);
  String modulePath = getStringParameter(config, "module-path", "app");
  module = getStringParameter(config, "config", "config");

  function = getStringParameter(config, "app", "app");
  int optlevel = getOptLevel(config);
  boolean debug = getDebug(config);
  boolean production = getBooleanParameter(config, "production", false);
  boolean verbose = getBooleanParameter(config, "verbose", false);
  boolean legacyMode = getBooleanParameter(config, "legacy-mode", false);

  Repository home = getHomeRepository(ringoHome, config.getServletContext());

  try {
   // Use ',' as platform agnostic path separator
   String[] paths = StringUtils.split(modulePath, ",");
   RingoConfiguration ringoConfig = new RingoConfiguration(home, paths, "modules");
   ringoConfig.setDebug(debug);
   ringoConfig.setVerbose(verbose);
   ringoConfig.setParentProtoProperties(legacyMode);
   ringoConfig.setStrictVars(!legacyMode && !production);
   ringoConfig.setReloading(!production);
   ringoConfig.setOptLevel(optlevel);
   theEngine = new RhinoEngine(ringoConfig, null);

   instance = this; // Save aside the last initialized filter (with a RhinoEngine!)

   // They can define the path to a file we watch to know when new javascript is deployed:
   this.triggerReloadsFilePath = getTriggerReloadsFilePath(config);
   if (this.triggerReloadsFilePath == null) {
    // But by default we look to the parent directory of the RINGO_HOME directory.
    // (in production for us this is a soft link which points to the latest
    // deployment of our scripts):
    this.triggerReloadsFilePath = ringoHome + "/..";
   }

   if (this.triggerReloadsFilePath != null) {
    File triggerReloadsFile = new File(this.triggerReloadsFilePath);
    if (triggerReloadsFile.exists()) {
     log.info("Monitoring the file/dir \"" + triggerReloadsFilePath + "\" to know when new javascript has been deployed.");
     this.triggerReloadsFileLastModified = triggerReloadsFile.lastModified();
    } else {
     // It could be they don't care about this feature (like on developer's PCs we don't
     // need this feature), or it could be they've misconfigured the path to the file:
     log.warn("No such file/dir \"" + this.triggerReloadsFilePath + "\" to monitor that new javascript has been deployed (disabling feature).");
     triggerReloadsFilePath = null;
    }
   }
  } catch (Exception e) {
   // Then disable javascript interception completely:
   log.error("Disabling javascript interceptions (ringo failed to initialize): " + e.getMessage());
   log.error("Failed to initialize ringo!");
   e.printStackTrace();
   ringoEnabled = false;
  }

  Context cx = theEngine.getContextFactory().enterContext();
  try {
   requestProto = new JsgiRequest(cx, theEngine.getScope());
  } catch (NoSuchMethodException nsm) {
   throw new ServletException(nsm);
  } finally {
   Context.exit();
  }

  return theEngine;
 }

 public void doFilter(ServletRequest request, ServletResponse response,
  FilterChain chain) throws IOException, ServletException {

  // if ringo is not enabled
  if (!ringoEnabled) {
   // just run the filter chain as a normal spring application:
   chain.doFilter(request, response);
   return;
  }

  // Let Ringo know about the filter chain:
  // this is used if the ringo code uses "runFilterChain" to delegate processing on to Spring MVC
  request.setAttribute(JSGI_INTERCEPTED_FILTER_CHAIN, chain);

  if (this.engine == null || triggerReloadsFileHasChanged()) {
   if (this.engine != null) {
    log.info("Invalidating all cached javascript files");
   }
   RhinoEngine newEngine = createEngine(this.filterConfig, true);
   if (newEngine != null) {
    // Replace the former engine
    this.engine = newEngine;
   } else {
    log.error("Trigger-Reload file/dir changed but failed to recreate the RhinoEngine.");
    log.error("The changed javascript files will not be visible without a server restart due to this error.");
   }
  }

  long startTime = System.currentTimeMillis();
  if (ringoEnabled) {

   Context cx = engine.getContextFactory().enterContext();
   try {
    // Note the final argument below is supposed to be the JsgiServlet which is normally made
    // available in javascript as "request.servlet" but for us is simply null.
    JsgiRequest req = new JsgiRequest(cx, (HttpServletRequest) request, (HttpServletResponse) response,
     requestProto, engine.getScope(), null);

    // For consistency's sake, add "filter" since there's no "servlet":
    Scriptable env = (Scriptable) ScriptableObject.getProperty(req, "env");
    ScriptableObject.defineProperty(env, "filter", Context.javaToJS(this, req), ScriptableObject.PERMANENT);

    engine.invoke("ringo/jsgi", "handleRequest", module, function, req);

    long endTime = System.currentTimeMillis();
    //* log.debug("Time to process " + ((HttpServletRequest)request).getRequestURI() +
    //*			" (via javascript): " + (endTime - startTime) + " milliseconds");
   } catch (Exception e) {
    if (isUnhandledRequestException(e)) {
     log.warn("Ringo threw unhandled request - running the servlet chain.");
     chain.doFilter(request, response);
    } else {
     try {
      renderError(e, (HttpServletResponse) response);
      RingoRunner.reportError(e, System.err, engine.getConfig().isVerbose());
     } catch (Exception failed) {
      // custom error reporting failed, rethrow original exception for default handling
      RingoRunner.reportError(e, System.err, false);
      throw new ServletException(e);
     }
    }
   } finally {
    Context.exit();
   }
  }
 }

 private boolean isUnhandledRequestException(Exception e) {
  boolean unhandledRequest = false;

  if (e instanceof JavaScriptException) {
   JavaScriptException jse = (JavaScriptException) e;
   String details = jse.details();
   if (details != null) {
    details = details.toLowerCase();
    if (details.contains("error") &&
     details.contains("unhandled request")) {
     unhandledRequest = true;
    }
   }
  }

  return unhandledRequest;
 }

 /**
  * Run the servlet filter chain allowing the normal processing (i.e. the servlet)
  * to generate the response.
  * @throws ServletException
  * @throws IOException
  */
 public static void runFilterChain(ServletRequest request, ServletResponse response)
 throws IOException, ServletException {

  FilterChain chain = (FilterChain) request.getAttribute(JSGI_INTERCEPTED_FILTER_CHAIN);
  chain.doFilter(request, response);
 }

 /**
  * Run the servlet filter chain allowing the normal processing (i.e. the servlet)
  * to generate the response, and capture it's output in an HttpServletResponseWrapper.
  *
  * From the returned wrapper you can use:
  *   getStatus() to get the http status code.
  *   getContentType() to get the generate http content type.
  *   getBody() to get the actual buffered response.
  *
  * @throws ServletException
  * @throws IOException
  */
 public static BufferedResponseWrapper captureFilterChain(ServletRequest request, ServletResponse response)
 throws IOException, ServletException {

  FilterChain chain = (FilterChain) request.getAttribute(JSGI_INTERCEPTED_FILTER_CHAIN);

  BufferedResponseWrapper responseWrapper = new BufferedResponseWrapper((HttpServletResponse) response);

  chain.doFilter(request, responseWrapper);

  // now let response wrapper send to the output stream
  // (i.e. if the caller continues to use it for generating the real response):
  responseWrapper.setBuffering(false);

  return responseWrapper;
 }

 public void destroy() {}

 // Has the "trigger reloads" file changed since the last time ringo was loaded?
 private boolean triggerReloadsFileHasChanged() {
  boolean triggerFileChanged = false;
  if (triggerReloadsFilePath != null) {
   File triggerReloadsFile = new File(this.triggerReloadsFilePath);
   if (triggerReloadsFile.exists()) {
    long newLastModified = triggerReloadsFile.lastModified();

    triggerFileChanged = (newLastModified != this.triggerReloadsFileLastModified);

    if (triggerFileChanged) {
     log.info("The file/dir \"" + this.triggerReloadsFilePath + "\" has changed (new javascript has been deployed).");
    }
   }
  }

  return triggerFileChanged;
 }

 /**
  * Return the RhinoEngine being used by the RingoJsgiFilter.
  * @return
  */
 public RhinoEngine getServletRhinoEngine() {
  return this.engine;
 }

 private String getRingoHome(FilterConfig config) {

  String ringoHome = null;

  String configParamName = "ringo-home";

  // The "scripting.home" system property is esp. handy
  // for developer PCs (i.e. to point at their working
  // dir for ringo scripts):
  String scriptsHome = System.getProperty("scripting.home");
  if (scriptsHome != null) {
   if ((new File(scriptsHome)).exists()) {
    ringoHome = scriptsHome;
   }
  }

  // But otherwise the "ringo-home" init-param in the
  // checked in web.xml points to where the scripts
  // really live on the deployment boxes:
  if (config.getInitParameter(configParamName) != null) {
   // But don't return that if the directory doesn't really exist:
   if ((new File(config.getInitParameter(configParamName))).exists()) {
    ringoHome = config.getInitParameter(configParamName);
   }
  }

  if (ringoHome != null) {
   // Normalize the path so that we use the actual path where
   // any softlinks are expanded to the full/actual path.
   // Without doing this, ringo keeps the old file descriptors
   // open and doesn't even recognize that the deployment
   // release soft link has changed!  This causes the deployed
   // javascript changes to not be picked up without a server
   // restart.
   File f = new File(ringoHome);
   try {
    String canonicalPath = f.getCanonicalPath();
    if (canonicalPath != null) {
     ringoHome = canonicalPath;
     log.info("The canonical path to RINGO_HOME is: " + ringoHome);
    }
   } catch (Exception e) {
    log.warn("Could not get the canonical path for RINGO_HOME, using it as provided: " + ringoHome);
   }
  } else {
   // Otherwise return "/WEB-INF" as a default.  We're not
   // storing scripts under "/WEB-INF", so normally this
   // just means to disable the javascript interception.
   ringoHome = "/WEB-INF";
  }

  return ringoHome;
 }

 /**
  * By default we do load the ringo engine on (filter) startup.
  * But we've seen some strangeness (what seems like a threading
  * race condition) that goes away if we re-load ringo after the
  * filter has already been initialized.  You can define either
  * of the following to false to disable the initial load of
  * ringo on startup (and instead lazy load it when the first
  * request comes in):
  *    scripting.loadRingoOnStartup  (a -D system prop)
  *    load-ringo-on-startup (a filter init param)
  **/
 private boolean getLoadRingoOnStartup(FilterConfig config) {

  boolean loadRingoOnStartup = true;
  String loadRingoOnStartupStr = null;

  // They can optionally use a -D system variable (so things could change
  // without deploying a new web.xml):
  loadRingoOnStartupStr = System.getProperty("scripting.loadRingoOnStartup");
  if (loadRingoOnStartupStr == null) {
   // But otherwise a servlet init param is used:
   loadRingoOnStartupStr = config.getInitParameter("load-ringo-on-startup");
  }

  if (loadRingoOnStartupStr != null) {
   loadRingoOnStartup = isTrueConfigParam(loadRingoOnStartupStr);
  }

  return loadRingoOnStartup;
 }

 private boolean getDebug(FilterConfig config) {

  String debugStr = null;

  // The "scripting.debug" system property can be used esp.
  // on developer PCs to initiate the Ringo debugger.
  // (the "debug" servlet parameter was accidentially getting
  // checked into ClearCase so we thought this would be better).
  debugStr = System.getProperty("scripting.debug");
  if (debugStr != null) {
   logScriptingDebugSettings(debugStr, "\"scripting.debug\" system property");
   if (!isTrueConfigParam(debugStr) && !isFalseConfigParam(debugStr)) {

    // Maybe they've given match(es) against the name(s)
    // of which ringo filter(s) they want to debug...
    String filterName = config.getFilterName();
    String matches[] = debugStr.split(",");
    boolean foundMatch = false;
    for (int i = 0; !foundMatch && i < matches.length; i++) {
     foundMatch = (filterName.toLowerCase().contains(matches[i].toLowerCase()));
    }
    if (foundMatch) {
     debugStr = "true";
    }
   }
  } else {
   // But otherwise the "debug" init-param in web.xml can still be used
   debugStr = config.getInitParameter("debug");
   if (debugStr != null) {
    log.error("WARNING!! DEBUG SETTING IN WEB.XML INSTEAD OF SCRIPTING.DEBUG PROPERTY!!");
    logScriptingDebugSettings(debugStr, "\"debug\" servlet init-param");
   }
  }

  if (debugStr != null) {
   return isTrueConfigParam(debugStr);
  }

  // But the default is not to debug!
  return false;
 }

 private int getOptLevel(FilterConfig config) {

  int optlevel = 0;

  // The "scripting.optlevel" system property can be used esp.
  // on developer PCs to set the Ringo/Rhino optimization level.
  // (note the profiler middleware requires the optimization
  // level to be -1)
  String optlevelStr = System.getProperty("scripting.optlevel");
  if (optlevelStr != null) {
   log.info("\"scripting.optlevel\" system property: " + optlevelStr);

   try {
    optlevel = Integer.parseInt(optlevelStr);
   } catch (NumberFormatException nfx) {
    log.error("Invalid value for optlevel: \"" + optlevelStr + "\"");
   }
  } else {
   // But otherwise the "debug" init-param in web.xml can still be used
   optlevel = getIntParameter(config, "optlevel", 0);
  }

  return optlevel;
 }

 // triggerReloadsFile is the path to *some file* that changes whenever new
 // javascript is deployed:
 private String getTriggerReloadsFilePath(FilterConfig config) {

  // They can use a -D system variable:
  String triggerReloadsFileSystemProp = System.getProperty("scripting.reloadIfModified");
  if (triggerReloadsFileSystemProp != null) {
   return triggerReloadsFileSystemProp;
  } else {
   // Or as a servlet param in web.xml:
   String triggerReloadsFileServletParam = config.getInitParameter("reload-if-modified");
   if (triggerReloadsFileServletParam != null) {
    return triggerReloadsFileServletParam;
   }
  }

  return null;
 }

 private boolean isTrueConfigParam(String value) {
  return ("true".equals(value) || "1".equals(value) || "on".equals(value));
 }

 private boolean isFalseConfigParam(String value) {
  return ("false".equals(value) || "0".equals(value) || "off".equals(value));
 }

 private void logScriptingDebugSettings(String debugSetting, String foundWhere) {
  log.info("Ringo debug: " + debugSetting + ", set from: " + foundWhere);
  String displayVar = System.getenv("DISPLAY");
  if (displayVar != null) {
   log.info("X Windows DISPLAY: " + displayVar);
  } else {
   log.info("X Windows DISPLAY not set.");
  }
 }

 protected void renderError(Throwable t, HttpServletResponse response)
 throws IOException {
  response.reset();
  InputStream stream = JsgiServlet.class.getResourceAsStream("error.html");
  byte[] buffer = new byte[1024];
  int read = 0;
  while (true) {
   int r = stream.read(buffer, read, buffer.length - read);
   if (r == -1) {
    break;
   }
   read += r;
   if (read == buffer.length) {
    byte[] b = new byte[buffer.length * 2];
    System.arraycopy(buffer, 0, b, 0, buffer.length);
    buffer = b;
   }
  }
  String template = new String(buffer, 0, read);
  String title = t.getMessage();
  StringBuffer body = new StringBuffer();
  if (t instanceof RhinoException) {
   RhinoException rx = (RhinoException) t;
   body.append("<p>In file <b>")
    .append(rx.sourceName())
    .append("</b> at line <b>")
    .append(rx.lineNumber())
    .append("</b></p>");
   List < SyntaxError > errors = RhinoEngine.errors.get();
   for (SyntaxError error: errors) {
    body.append(error.toHtml());
   }
   body.append("<h3>Script Stack</h3><pre>")
    .append(rx.getScriptStackTrace())
    .append("</pre>");
  }
  template = template.replaceAll("<% title %>", title);
  template = template.replaceAll("<% body %>", body.toString());
  response.setStatus(500);
  response.setContentType("text/html");
  response.getWriter().write(template);
 }

 protected String getStringParameter(FilterConfig config, String name, String defaultValue) {
  String value = config.getInitParameter(name);
  return value == null ? defaultValue : value;
 }

 protected int getIntParameter(FilterConfig config, String name, int defaultValue) {
  String value = config.getInitParameter(name);
  if (value != null) {
   try {
    return Integer.parseInt(value);
   } catch (NumberFormatException nfx) {
    log.error("Invalid value for parameter \"" + name + "\": " + value);
   }
  }
  return defaultValue;
 }

 protected boolean getBooleanParameter(FilterConfig config, String name, boolean defaultValue) {
  String value = config.getInitParameter(name);
  if (value != null) {
   if ("true".equals(value) || "1".equals(value) || "on".equals(value)) {
    return true;
   }
   if ("false".equals(value) || "0".equals(value) || "off".equals(value)) {
    return false;
   }
   log.error("Invalid value for parameter \"" + name + "\": " + value);
  }
  return defaultValue;
 }
}
