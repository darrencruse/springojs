/**
 * @fileOverview Utility module for accessing Spring Beans from javascript.
 *
 * Note: spring beans are retrieved from the Spring Application Context.
 *
 * @author darrencruse (https://github.com/darrencruse)
 */

importClass(org.springo.ApplicationContextProvider);

/**
 * Inject one or more beans from Spring into the specified javascript object.
 *
 * Most commonly, it's expected you would do this near the top of your module
 * e.g.:
 *
 *   var spring = require("springcontext");
 *   spring.inject(this,
 *       "managePlatformService",
 *       "jsonDataService");
 *
 * The result is that two module scoped variables "managePlatformService"
 * and "jsonDataService" are added in the current module, pointing at the
 * beans of the same names configured in Spring.
 *
 * If the bean names configured in Spring are not to your liking, you can give
 * them different names by passing an Array containing [<aliasName>, <beanName>]
 * instead of one or more of the bean names e.g.:
 *
 *   spring.inject(this, ["mps","managePlatformService"], "jsonDataService");
 *
 * Where in this example the variable "mps" is added to your module,
 * pointing at the "managePlatformService" spring bean (and "jsonDataService"
 * is added normally using the "jsonDataService" bean name as the variable name).
 *
 * Note:  If any of the specified bean names are not found in Spring, a
 *        NoSuchBeanException is thrown.
 *
 * @Param {Object}  the javascript object to inject the specified beans into.
 * @param {String|Array} one or more bean names (or alias/beanName pairs) as arguments.
 */
exports.inject = function(injectee, beanArgs) {

	// Get each of the named beans from Spring:
	for(var i=1; i < arguments.length; i++) {
		var beanSpec = arguments[i];
		var varName;
		var beanName;
		if(beanSpec instanceof Array) {
			varName = beanSpec[0];
			beanName = beanSpec[1];
		}
		else {
			varName = beanName = beanSpec;
		}
		var bean = ApplicationContextProvider.getBean(beanName);

		// inject this bean into the target (typically a module object)
		injectee[varName] = bean;
	}
}

/**
 * Get a bean from Spring via a "getter" method.
 *
 * This is an alternative to "inject" above.  It uses Rhino's
 * "missing method" technique to expose bean names as implicit
 * "getter methods" on the "springcontext" module itself, e.g.:
 *
 *    var spring = require("springcontext");
 *    var managePlatformService = spring.getManagePlatformService();
 *
 * The "getter method" name is "get" followed by the bean name
 * as configured in the Spring ApplicationContext.
 *
 * Note: this works fine, but in practice we have used the "inject"
 * method more (possibly the "getter method" approach feels more like
 * a java convention and less like javascript - also note __noSuchMethod__
 * was a Mozilla convention so this would not work e.g. on node.js).
 *
 * @return the specified bean (otherwise NoSuchBeanException is thrown)
 */

exports.__noSuchMethod__ = function(name, params) {

	// We're expecting them to do like "get<BeanName>"
	if(name.indexOf("get") == 0) {
		var beanName = name[3].toLowerCase() + name.substring(4, name.length);
		return(ApplicationContextProvider.getBean(beanName));
	}
}
