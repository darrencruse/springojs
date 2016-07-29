/**
 * @fileOverview Middleware for modifying the servlet request object for a
 * RESTful JSON service request.
 *
 * You configure it with a function that:
 *
 * a.  Takes the original servlet request object as a parameter, and
 * b.  Returns the servlet request you want forwarded to spring.
 *
 * Note:  This is not just modifying the ringo Request,  but the underlying
 * HttpServletRequest as seen by the entire servlet pipeline.  Since
 * HttpServletRequest is read only, this assumes the underlying
 * HttpServletRequest has been wrapped in an HttpServletRequestWrapper.
 *
 * @example
 * app.configure("notfound", "error", "jsonerror","modifyservletrequest");
 * app.modifyservletrequest = function(servletRequest) {
 *    servletRequest.setHeader("If-Modified-Since", "Sat, 29 Oct 1994 19:43:31 GMT");
 *    return servletRequest;
 * };
 *
 * @author darrencruse (https://github.com/darrencruse)
 */

/**
 * Stick middleware modifying the servlet request prior to the Spring controller.
 * @param {Function} next the wrapped middleware chain
 * @param {Object} app the Stick Application object
 * @returns {Function} a JSGI middleware function
 */
exports.middleware = function modifyservletrequest(next, app) {

	app.modifyservletrequest = function(servletRequest) { return servletRequest; };

  return function modifyservletrequest(request) {

		// Get the servlet request wrapper
		var requestWrapper = request.env.servletRequest;

		if(typeof(app.modifyservletrequest) === 'function') {
			// replace ringo's servlet request with the modified version,
			// if the function returned something
			var modifiedRequest = app.modifyservletrequest(requestWrapper);
			if(typeof(modifiedRequest) != 'undefined' && modifiedRequest) {
				request.env.servletRequest = modifiedRequest;
			}

		}

		// Okay modified the request - pass it down the chain:
		return next(request);
  };
};
