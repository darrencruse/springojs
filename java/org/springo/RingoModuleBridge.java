package org.springo;

import java.io.File;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.ringojs.engine.RhinoEngine;
import org.ringojs.repository.FileRepository;
import org.ringojs.repository.Repository;
import org.ringojs.tools.RingoConfiguration;
import org.ringojs.util.StringUtils;
import org.ringojs.wrappers.ScriptableMap;

/**
 * Invoke Ringo CommonJS module javascript from java, via a Spring configured
 * "Module Bridge".
 *
 * You can spring configure the bridge to give access to the functions of
 * a single module, or to give access to the functions of any module
 * (in which case you pass the module name when you invoke the function).
 *
 * The extra arguments passed from java when calling invokeMethod()
 * or invokeFunction() are passed to javascript using Rhino/Ringo's
 * standard rules for "wrapping" java types as javascript types.
 *
 * This means simple types like Strings and Integers go through as
 * expected, while complex java types go through as their
 * java types and must be manipulated using their *java* api.
 *
 * Note: when in doubt - the ringo debugger is your friend!!
 *
 * Exceptions:
 *
 * 1.  Java Beans with getters and setters can be accessed using
 *     javascript property conventions where e.g. "bean.getFirstName()"
 *     becomes simply "bean.firstName".
 *
 *     (this is standard Rhino)
 *
 * 2.  Maps passed as args from Java appear as Javascript objects i.e.
 *     instead of "map.get('property')" you can do "map.property".
 *
 *     (this is an enhancement we've made that only applies when invoking
 *     javascript via the RingoModuleBridge).
 *
 * @author darrencruse (https://github.com/darrencruse)
 */
public class RingoModuleBridge {

 private static final Log logger = LogFactory.getLog(RingoModuleBridge.class);

 // Should we borrow the JsgiFilter's javascript "RhinoEngine",
 // instead of creating our own?
 private boolean specifiedBorrowRhinoEngine = false;
 private boolean borrowRhinoEngine = true;

 // configuration properties
 // (override defaults below in spring context xml file)
 private boolean specifiedRingoHome = false;
 private String ringoHome = "/WEB-INF";
 private String modulePath = "app";
 private int optlevel = 0;
 // Even though it looks like the debug option should work
 // the same as JsgiServlet, right now it only seems to
 // work properly if you enable it through web.xml:
 private boolean debug = false;
 private boolean production = false;
 private boolean verbose = false;
 private boolean legacyMode = false;

 // They have the choice of configuring different RingoModuleBridge's
 // for different modules (in which case they configure the modules
 // name using Spring, or simply passing the module name when they
 // invoke module function's.
 private String module = null;

 RhinoEngine engine = null;

 public RingoModuleBridge() {}

 public void init() throws Exception {
  if (engine != null) {
   logger.debug("Reinitializing RhinoEngine on next request of the RingoModuleBridge...");
  }
  engine = null;
 }

 /**
  * This helper function "lazy initializes" the RhinoEngine upon the first
  * invokeFunction/invokeMethod call made *after* the RingoModuleBridge has
  * been initialized by Spring.
  *
  * The reason this initialization is "lazy" is to ensure that the JsgiServlet
  * servlet has initialized *before* we attempt to use it's RhinoEngine, i.e.
  * in the case where RingoModuleBridge is sharing JsgiServlet's RhinoEngine.
	*
	* This is critical in the case where javascript functions are invoked via
	* the RingoModuleBridge that attempt to access state variables set via the
	* Ringo filter javascript.
  *
  * @return
  * @throws Exception
  */
 private RhinoEngine getRhinoEngine() throws Exception {

  if (engine != null) {
   return engine; // it's already been initialized.
  }

  // If in spring they specify "borrowRhinoEngine" that we use what's configured
  // in web.xml, otherwise if in spring they specify ringoHome they must want their
  // own engine.
  boolean borrowEngine = (specifiedBorrowRhinoEngine ? borrowRhinoEngine :
   (specifiedRingoHome ? false : true));
  if (borrowEngine) {
   JsgiFilter ringoFilter = JsgiFilter.instance;
   if (ringoFilter != null) {
    // Note:  Admin now uses two ringo filters - we've arranged things in web.xml
    //        so the api filter is created last and is the one we get here.
    engine = ringoFilter.getServletRhinoEngine();
   } else {
    logger.error("The RingoModuleBridge is configured for use of the JsgiFilter's engine but JsgiFilter has not initialized!");
   }
  } else {
   try {
    String homeDir = getRingoHome();
    Repository homeRepo = new FileRepository(homeDir);
    //logger.debug("Using file repository for ringo home \"" + homeDir + " (" + homeRepo + ")");

    // Use ',' as platform agnostic path separator
    String[] paths = StringUtils.split(modulePath, ",");
    RingoConfiguration ringoConfig = new RingoConfiguration(homeRepo, paths, "modules");
    ringoConfig.setDebug(debug);
    ringoConfig.setVerbose(verbose);
    ringoConfig.setParentProtoProperties(legacyMode);
    ringoConfig.setStrictVars(!legacyMode && !production);
    ringoConfig.setReloading(!production);
    ringoConfig.setOptLevel(optlevel);
    engine = new RhinoEngine(ringoConfig, null);
   } catch (Exception x) {
    logger.error("Failed to initialize RhinoEngine");
    throw new Exception(x);
   }
  }

  return engine;
 }

 /**
  * Invoke a javascript function exported by the configured module
  * Note:  This name mimics the javax.script.Invocable standard from JSR223.
  */
 public Object invokeFunction(String functionName, Object...args) throws Exception {
  return invokeMethod(this.module, functionName, args);
 }

 /**
  * Invoke a javascript function exported by the specified module
  * Note:  This name mimics the javax.script.Invocable standard from JSR223.
  */
 public Object invokeMethod(String moduleName, String functionName, Object...args) throws Exception {

  Object result = null;

  RhinoEngine theEngine = getRhinoEngine();

  Context cx = theEngine.getContextFactory().enterContext();
  try {
   // Load the specified module.
   // Note:  Ringo's RhinoEngine returns previously loaded modules from cache.
   Scriptable parent = theEngine.loadModule(cx, moduleName, null);

   // Normally they're invoking an *exported* function, but be nice if
   // they try and use a simple javascript file that's not a module:
   Object
   function = ScriptableObject.getProperty(parent, functionName);
   if (!(function instanceof Function)) {

    // The named function was not at the top level, check the exports:
    Scriptable exports = (Scriptable) ScriptableObject.getProperty(parent, "exports");
    if (exports == null) {
     // The named function was not top level and there were no exports
     throw new Exception("Malformed module \"" + moduleName + "\": could not find any exports");
    }

    function = ScriptableObject.getProperty(exports, functionName);
    if (function instanceof Function) {
     // The exports contain the desired function
     parent = exports;
    } else {
     throw new Exception("No such function \"" + functionName + "\" in module \"" + moduleName + "\"");
    }
   }

   convertMapArgsToJson(parent, args);

   // We found the function invoke it with Ringo's RhinoEngine:
   result = theEngine.invoke(parent, functionName, args);
  } finally {
   Context.exit();
  }

  return result;
 }

 protected void convertMapArgsToJson(Scriptable scope, Object[] args) {
  if (args != null) {
   for (int i = 0; i < args.length; i++) {
    if (args[i] instanceof Map < ? , ? > ) {
     ScriptableMap jsonScriptable = new ScriptableMap(scope, (Map) args[i]);
     args[i] = jsonScriptable;
    }
   }
  }
 }

 public String getRingoHome() {
  // The "scripting.home" system property is meant as an
  // override for developer PCs (i.e. to point at their
  // ClearCase view for StaticContent which is where the
  // ringo scripts are being checked in):
  String scriptsHome = System.getProperty("scripting.home");
  if (scriptsHome != null) {
   if ((new File(scriptsHome)).exists()) {
    return scriptsHome;
   } else {
    logger.error("Defined \"scripting.home\" system property does not exist (" + scriptsHome + ")");
   }
  }

  return ringoHome;
 }

 /**
  * Should we use JsgiServlet's javascript "RhinoEngine",
  * instead of creating our own?
  *
  * Note:  This is recommended if the current app is using
  *        the JsgiServlet, but standalone java command line
  *        programs might configure a separate RhinoEngine.
  *
  * @return
  */
 public boolean getBorrowRhinoEngine() {
  return borrowRhinoEngine;
 }

 public void setBorrowRhinoEngine(boolean useJsgiServletEngine) {
  this.specifiedBorrowRhinoEngine = true; // they indicated what they want
  this.borrowRhinoEngine = useJsgiServletEngine; // and this is what they want
 }

 public void setRingoHome(String ringoHome) {
  this.specifiedRingoHome = true;
  this.ringoHome = ringoHome;
 }

 public String getModulePath() {
  return modulePath;
 }

 public void setModulePath(String modulePath) {
  this.modulePath = modulePath;
 }

 public int getOptlevel() {
  return optlevel;
 }

 public void setOptlevel(int optlevel) {
  this.optlevel = optlevel;
 }

 public boolean isDebug() {
  return debug;
 }

 public void setDebug(boolean debug) {
  this.debug = debug;
 }

 public boolean isProduction() {
  return production;
 }

 public void setProduction(boolean production) {
  this.production = production;
 }

 public boolean isVerbose() {
  return verbose;
 }

 public void setVerbose(boolean verbose) {
  this.verbose = verbose;
 }

 public boolean isLegacyMode() {
  return legacyMode;
 }

 public void setLegacyMode(boolean legacyMode) {
  this.legacyMode = legacyMode;
 }

 public String getModule() {
  return module;
 }

 public void setModule(String module) {
  this.module = module;
 }
}
