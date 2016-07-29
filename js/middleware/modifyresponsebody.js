/**
 * @fileOverview Middleware for modifying the JSON response body returned by
 * a RESTful JSON request.
 *
 * You configure it with a function that:
 *
 * a.  Takes the (Spring generated) JSON response as a parameter, and
 * b.  Returns the JSON you want sent to the client.
 *
 * Note:  This is not just modifying the ringo Response,  but the underlying
 * HttpServletResponse as seen by the entire servlet pipeline.  Since
 * HttpServletResponse is read only, this assumes the underlying
 * HttpServletResponse has been wrapped in an HttpServletResponseWrapper.
 *
 * @example
 *    app.configure("notfound", "error", "jsonerror", "modifyresponsebody");
 *    app.modifyresponsebody = function(json) {
 *        json.someModifiedProperty = true;
 *    };
 */

include('ringo/webapp/response');

importClass(org.springo.BufferedResponseWrapper);

var utils = require("springoutils");

/**
 * Stick middleware modifying the JSON response returned by the Spring controller.
 * @param {Function} next the wrapped middleware chain
 * @param {Object} app the Stick Application object
 * @returns {Function} a JSGI middleware function
 */

exports.middleware = function modifyresponsebody(next, app) {

	app.modifyresponsebody = function(jsonRequest) { return jsonRequest; };

  return function modifyresponsebody(request) {

		// Create our servlet response wrapper which captures Spring's output:
		var originalResponse = request.env.servletResponse;
		var responseWrapper = new BufferedResponseWrapper(originalResponse);
		request.env.servletResponse = responseWrapper;

		// Go ahead and run the chain to get the (ringo) Response from Spring:
		// Note:  our response wrapper has buffered the spring response - we
		//        actually ignore the ringo Response object.
		var springResponse = next(request);

		// Is this JSON?
		var response = responseWrapper.body;
		var contentType = responseWrapper.contentType;
		var jsonResponse = "";
		if(contentType && contentType.indexOf('json') != -1) {

			// Note:  It's important the following variable is named 'jsonResponse' cause that's how we're
			// telling them to refer to the response body in their config.js (eval'ed) expression
			jsonResponse = utils.jsonParse(response);
		}

		if(typeof(app.modifyresponsebody) === 'function') {
			// pass the JSON to their modifier function:
			var modifiedJsonResponse = app.modifyresponsebody(jsonResponse);
			if(typeof(modifiedJsonResponse) != 'undefined' && modifiedJsonResponse) {
				jsonResponse = modifiedJsonResponse;
			}
		}

		// Convert the modified response back to a string if needed:
		var modifiedJsonResponseStr = "";
		if(contentType && contentType.indexOf('json') != -1) {
			modifiedJsonResponseStr = JSON.stringify(jsonResponse);
		}
		else {
			// the response was not JSON:
			modifiedJsonResponseStr = response;
		}

		// let the response wrapper return our response to the real output stream:
		responseWrapper.buffering = false;

		// return a ringo Response with our modifications:
		var modifiedResponse = new Response(modifiedJsonResponseStr);
		modifiedResponse.status = responseWrapper.status;
		modifiedResponse.contentType = responseWrapper.contentType;
		return modifiedResponse;
  };
};
