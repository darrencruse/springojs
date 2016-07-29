/**
 * @fileOverview Middleware for modifying the JSON request body for a
 * RESTful service request.
 *
 * You configure it with a function that:
 *
 * a.  Takes the original JSON request body as a parameter, and
 * b.  Returns the JSON you want forwarded to spring.
 *
 * Note:  This is not just modifying the ringo Request,  but the underlying
 * HttpServletRequest as seen by the entire servlet pipeline.  Since
 * HttpServletRequest is read only, this assumes the underlying
 * HttpServletRequest has been wrapped in an HttpServletRequestWrapper.
 *
 * @example
 * app.configure("notfound", "error", "jsonerror", "modifyrequestbody");
 * app.modifyrequestbody = function(json) {
 *   json.users[0] = "Fred";
 *   return json;
 * };
 *
 * @author darrencruse (https://github.com/darrencruse)
 */

 var utils = require("springoutils");

/**
 * Stick middleware modifying the JSON request body prior to the Spring controller.
 * @param {Function} next the wrapped middleware chain
 * @param {Object} app the Stick Application object
 * @returns {Function} a JSGI middleware function
 */
exports.middleware = function modifyrequestbody(next, app) {

	app.modifyrequestbody = function(jsonRequest) { return jsonRequest; };

  return function modifyrequestbody(request) {

		// Get the servlet request wrapper
		var requestWrapper = request.env.servletRequest;

		// Get the JSON for the request body
		var jsonRequestBodyStr = requestWrapper.body;
		var jsonRequestBody = utils.jsonParse(jsonRequestBodyStr);

		if(typeof(app.modifyrequestbody) === 'function') {
			// modify the request and set it back on the servlet request wrapper
			var modifiedJsonRequest = app.modifyrequestbody(jsonRequestBody);
			if(typeof(modifiedJsonRequest) == 'undefined' || !modifiedJsonRequest) {
				// They didn't return anything assume they just modified the original:
				modifiedJsonRequest = jsonRequestBody;
			}
			var modifiedJsonRequestStr = JSON.stringify(modifiedJsonRequest);
			requestWrapper.setBody(modifiedJsonRequestStr);
		}

		// Okay modified the request - pass it down the chain:
		return next(request);
  };
};
