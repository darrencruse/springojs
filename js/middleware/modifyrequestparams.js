/**
 * @fileOverview Middleware for overriding one or more query parameters of
 * the incoming request.
 *
 * You configure it with an object whose properties corresponding to the parameters
 * you want to override.
 *
 * Note:  This is not just modifying the ringo Request,  but the underlying
 * HttpServletRequest as seen by the entire servlet pipeline.  Since
 * HttpServletRequest is read only, this assumes the underlying HttpServletRequest
 * has already been wrapped to provide a setParameter() method.
 *
 * @example
 * app.configure("notfound", "error", "jsonerror","modifyrequestparams");
 * app.modifyrequestparams = { sessionId: 'modifiedsessionid' };
 *
 * @author darrencruse (https://github.com/darrencruse)
 */

/**
 * Stick middleware modifying request parameter(s) prior to the Spring controller.
 * @param {Function} next the wrapped middleware chain
 * @param {Object} app the Stick Application object
 * @returns {Function} a JSGI middleware function
 */
exports.middleware = function modifyrequestparams(next, app) {

	app.modifyrequestparams = { };

  return function modifyrequestparams(request) {

		// Get the servlet request wrapper
		var servletRequestWrapper = request.env.servletRequest;

		// In javascript "arguments" is an array of all arguments to this function...
		for(var prop in app.modifyrequestparams) {

			// Set the specified parameter value
			servletRequestWrapper.setParameter(prop, app.modifyrequestparams[prop]);
		}

		// Okay modified the request - pass it down the chain:
		return next(request);
  };
};
