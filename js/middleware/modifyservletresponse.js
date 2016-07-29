/**
 * @fileOverview Middleware for modifying the servlet response to a
 * RESTful JSON service request.
 *
 * You configure it with a function that:
 *
 * a.  Takes the (Spring generated) servlet response object as a parameter, and
 * b.  Returns the servlet response you want sent to the client.
 *
 * Note:  This is not just modifying the ringo Response,  but the underlying
 * HttpServletResponse as seen by the entire servlet pipeline.  Since
 * HttpServletResponse is read only, this assumes the underlying
 * HttpServletResponse has been wrapped in an HttpServletResponseWrapper.
 *
 * @example
 * app.configure("notfound", "error", "jsonerror","modifyservletresponse");
 * app.modifyservletresponse = function(servletResponse) {
 *    if(servletResponse.status == 201) {
 *        servletResponse.status = 302;
 *    }
 *    return servletResponse;
 * };
 *
 * @author darrencruse (https://github.com/darrencruse)
 */

include('ringo/webapp/response');

importClass(org.springo.BufferedResponseWrapper);

/**
 * Stick middleware modifying the servlet response returned by the Spring controller.
 * @param {Function} next the wrapped middleware chain
 * @param {Object} app the Stick Application object
 * @returns {Function} a JSGI middleware function
 */
exports.middleware = function modifyservletresponse(next, app) {

	app.modifyservletresponse = function(servletResponse) { return servletResponse; };

  return function modifyservletresponse(request) {

		// Create our servlet response wrapper which captures Spring's output:
		var originalResponse = request.env.servletResponse;
		var responseWrapper = new BufferedResponseWrapper(originalResponse);
		request.env.servletResponse = responseWrapper;

		// Go ahead and run the chain to get the (ringo) Response from Spring:
		// Note:  our response wrapper has buffered the spring response - we
		//        actually ignore the ringo Response object.
		var springResponse = next(request);

		if(typeof(app.modifyservletresponse) === 'function') {
			// replace ringo's servlet response with the modified version
			var modifiedResponseWrapper = app.modifyservletresponse(responseWrapper);
			if(typeof(modifiedResponseWrapper) != 'undefined' && modifiedResponseWrapper) {
				responseWrapper = modifiedResponseWrapper;
				request.env.servletResponse = responseWrapper;
			}
		}

		var responseBody = responseWrapper.body;

		// let the response wrapper return our response to the real output stream:
		responseWrapper.buffering = false;

		// return a ringo Response with our modifications:
		var modifiedResponse = new Response(responseBody);
		modifiedResponse.status = responseWrapper.status;
		modifiedResponse.contentType = responseWrapper.contentType;
		return modifiedResponse;
  };
};
